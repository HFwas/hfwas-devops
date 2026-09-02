package com.hfwas.devops.fileparser.ocr;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 常驻 Python OCR 进程：一行 JSON 请求，一行 JSON 响应。
 * stdout 只走协议，日志在 stderr。
 */
@Slf4j
public class OcrPythonWorker implements AutoCloseable {

    private static final String BUNDLED_SCRIPT = "/ocr/ocr_worker.py";
    private static final String[] MODEL_DIRS = {
            "PP-OCRv6_medium_det_onnx",
            "PP-OCRv6_medium_rec_onnx"
    };
    private static final String[] MODEL_FILES = {
            "inference.onnx", "inference.yml", "inference.json"
    };
    /** 本地模型加载；无需再等 HuggingFace 下载。 */
    private static final long READY_TIMEOUT_MS = 60_000;
    private static final AtomicLong ID = new AtomicLong();

    private final ReentrantLock lock = new ReentrantLock();
    private final long timeoutMs;
    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private volatile boolean closed;

    public OcrPythonWorker(String pythonPath, String scriptPath, long timeoutMs) throws IOException {
        this(pythonPath, scriptPath, timeoutMs, Map.of());
    }

    public OcrPythonWorker(String pythonPath, String scriptPath, long timeoutMs,
                           Map<String, String> extraEnv) throws IOException {
        this.timeoutMs = timeoutMs;
        ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
        pb.redirectErrorStream(false);
        if (extraEnv != null) {
            extraEnv.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    pb.environment().put(k, v);
                }
            });
        }
        this.process = pb.start();
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        drainStderr(process.getErrorStream());
        waitReady();
        log.info("Python OCR worker ready: {}", scriptPath);
    }

    public static Path extractBundledScript() throws IOException {
        URL url = OcrPythonWorker.class.getResource(BUNDLED_SCRIPT);
        if (url == null) {
            throw new FileNotFoundException("classpath " + BUNDLED_SCRIPT + " not found");
        }
        if ("file".equals(url.getProtocol())) {
            try {
                return Path.of(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("invalid classpath OCR worker path", e);
            }
        }
        Path dest = Path.of(System.getProperty("user.home"), ".hfwas-devops", "ocr", "ocr_worker.py");
        Files.createDirectories(dest.getParent());
        copyResource(BUNDLED_SCRIPT, dest, true);
        Path modelsRoot = dest.getParent().resolve("models");
        for (String name : MODEL_DIRS) {
            extractModelDir(name, modelsRoot.resolve(name));
        }
        return dest;
    }

    private static void extractModelDir(String name, Path dest) throws IOException {
        Files.createDirectories(dest);
        for (String file : MODEL_FILES) {
            String resource = "/ocr/models/" + name + "/" + file;
            URL url = OcrPythonWorker.class.getResource(resource);
            if (url == null) {
                if ("inference.json".equals(file)) {
                    continue;
                }
                throw new FileNotFoundException("classpath " + resource + " not found");
            }
            Path target = dest.resolve(file);
            if (Files.exists(target) && Files.size(target) > 0) {
                continue;
            }
            copyResource(resource, target, false);
        }
    }

    private static void copyResource(String resource, Path dest, boolean replace) throws IOException {
        Files.createDirectories(dest.getParent());
        try (InputStream in = OcrPythonWorker.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new FileNotFoundException("classpath " + resource + " not found");
            }
            if (replace) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(in, dest);
            }
        }
    }

    public boolean isAvailable() {
        return !closed && process != null && process.isAlive();
    }

    public Result recognize(File image) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("Python OCR worker is closed");
        }
        String id = Long.toString(ID.incrementAndGet());
        JSONObject req = JSONUtil.parseObj(Map.of(
                "id", id,
                "image", image.getAbsolutePath()
        ));
        lock.lock();
        try {
            stdin.write(req.toString());
            stdin.newLine();
            stdin.flush();
            JSONObject resp = readResponse();
            if (!id.equals(resp.getStr("id"))) {
                throw new IOException("OCR worker id mismatch: sent " + id + " got " + resp.getStr("id"));
            }
            boolean ok = Boolean.TRUE.equals(resp.getBool("ok"));
            return new Result(ok, resp.getStr("text", ""), resp.getDouble("confidence", 0.0));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        lock.lock();
        try {
            if (stdin != null && process != null && process.isAlive()) {
                try {
                    stdin.write("{\"cmd\":\"shutdown\"}");
                    stdin.newLine();
                    stdin.flush();
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // fall through to destroy
                }
            }
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            closeQuietly(stdin);
            closeQuietly(stdout);
            lock.unlock();
        }
    }

    private void waitReady() throws IOException {
        String line = readLine(Math.max(timeoutMs, READY_TIMEOUT_MS));
        if (line == null) {
            throw new IOException("Python OCR worker produced no ready signal");
        }
        JSONObject msg = JSONUtil.parseObj(line);
        if (!Boolean.TRUE.equals(msg.getBool("ok")) || !"ready".equals(msg.getStr("event"))) {
            throw new IOException("Python OCR worker failed to start: " + line);
        }
    }

    private JSONObject readResponse() throws IOException {
        String line = readLine(timeoutMs);
        if (line == null) {
            throw new IOException("Python OCR worker timed out or exited");
        }
        return JSONUtil.parseObj(line);
    }

    private String readLine(long waitMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive() && !stdout.ready()) {
                return null;
            }
            if (stdout.ready()) {
                return stdout.readLine();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted waiting for OCR worker", e);
            }
        }
        return stdout.ready() ? stdout.readLine() : null;
    }

    private static void drainStderr(InputStream err) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[ocr-python] {}", line);
                }
            } catch (IOException ignored) {
                // process closed
            }
        }, "ocr-python-stderr");
        t.setDaemon(true);
        t.start();
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

    public record Result(boolean ok, String text, double confidence) {}
}
