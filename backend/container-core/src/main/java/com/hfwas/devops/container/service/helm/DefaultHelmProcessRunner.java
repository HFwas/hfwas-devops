package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultHelmProcessRunner implements HelmProcessRunner {

    static final int MAX_CAPTURE_BYTES = 64 * 1024;

    @Override
    public HelmProcessResult run(HelmProcessCommand command) {
        Path workDir = command.workDir();
        Path stdout = workDir.resolve("stdout.txt");
        Path stderr = workDir.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command.argv());
        builder.directory(workDir.toFile());
        builder.redirectOutput(stdout.toFile());
        builder.redirectError(stderr.toFile());
        if (command.environment() != null) {
            builder.environment().putAll(command.environment());
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED,
                    "无法执行 helm: " + e.getMessage());
        }
        writeStdin(process, command.stdin());
        boolean finished;
        try {
            finished = process.waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "helm 执行被中断");
        }
        if (!finished) {
            process.destroyForcibly();
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "helm 执行超时");
        }
        return new HelmProcessResult(process.exitValue(), readLimited(stdout), readLimited(stderr));
    }

    private static void writeStdin(Process process, byte[] stdin) {
        try (OutputStream os = process.getOutputStream()) {
            if (stdin != null && stdin.length > 0) {
                os.write(stdin);
            }
        } catch (IOException e) {
            process.destroyForcibly();
            throw new BizException(ContainerErrorCode.HELM_CHART_PUSH_FAILED, "写入 helm 标准输入失败");
        }
    }

    private static String readLimited(Path path) {
        try {
            if (!Files.exists(path)) {
                return "";
            }
            long size = Files.size(path);
            try (InputStream in = Files.newInputStream(path)) {
                int toRead = (int) Math.min(size, MAX_CAPTURE_BYTES);
                byte[] buf = in.readNBytes(toRead);
                return new String(buf, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "";
        }
    }
}
