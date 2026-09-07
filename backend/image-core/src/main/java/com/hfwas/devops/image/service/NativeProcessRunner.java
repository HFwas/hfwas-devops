package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NativeProcessRunner {

    static final long MAX_CAPTURE_BYTES = 8L * 1024 * 1024;

    private final ImageWorkLimiter limiter;
    private final Duration timeout;

    public NativeProcessRunner(ImageProcessorConfig config) {
        this(config, new ImageWorkLimiter(config));
    }

    @Autowired
    public NativeProcessRunner(ImageProcessorConfig config, ImageWorkLimiter limiter) {
        this.limiter = limiter;
        this.timeout = config.getEngines().getProcessTimeout();
    }

    public String run(List<String> command, Path workDir) {
        return run(command, workDir, timeout);
    }

    public String run(List<String> command, Path workDir, Duration commandTimeout) {
        Duration wait = commandTimeout == null ? timeout : commandTimeout;
        return limiter.call(() -> doRun(command, workDir, wait));
    }

    /**
     * 已持有 {@link ImageWorkLimiter} 时使用，避免 magick convert 后再 identify 尺寸发生重入死锁。
     */
    public String runUnlocked(List<String> command, Path workDir, Duration commandTimeout) {
        Duration wait = commandTimeout == null ? timeout : commandTimeout;
        return doRun(command, workDir, wait);
    }

    private String doRun(List<String> command, Path workDir, Duration commandTimeout) {
        Path stdout = workDir.resolve("proc-out.txt");
        Path stderr = workDir.resolve("proc-err.txt");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectOutput(stdout.toFile());
        pb.redirectError(stderr.toFile());
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new BizException(ResultCode.OPERATION_FAILED, "无法启动处理进程: " + e.getMessage());
        }
        boolean finished;
        try {
            finished = process.waitFor(commandTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.OPERATION_FAILED, "图片处理被中断");
        }
        if (!finished) {
            process.destroyForcibly();
            throw new BizException(ResultCode.OPERATION_FAILED, "图片处理超时");
        }
        String out = readQuietly(stdout);
        String err = readQuietly(stderr);
        if (process.exitValue() != 0) {
            log.warn("Native process failed cmd={} code={} err={}", command, process.exitValue(), err);
            throw new BizException(ResultCode.OPERATION_FAILED, "图片处理失败: " + firstLine(err, out));
        }
        return out;
    }

    private static String readQuietly(Path path) {
        try {
            if (!Files.exists(path)) {
                return "";
            }
            long size = Files.size(path);
            if (size <= MAX_CAPTURE_BYTES) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            log.warn("Native process output truncated from {} bytes at {}", size, path);
            try (InputStream in = Files.newInputStream(path)) {
                return new String(in.readNBytes((int) MAX_CAPTURE_BYTES), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static String firstLine(String err, String out) {
        String text = (err == null || err.isBlank()) ? out : err;
        if (text == null || text.isBlank()) {
            return "unknown error";
        }
        int nl = text.indexOf('\n');
        String line = nl < 0 ? text : text.substring(0, nl);
        return line.length() > 200 ? line.substring(0, 200) : line.trim();
    }
}
