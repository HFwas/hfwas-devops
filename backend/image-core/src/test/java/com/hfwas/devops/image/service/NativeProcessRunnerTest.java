package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeProcessRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void runUnlockedDoesNotWaitForLimiter() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.getEngines().setMaxConcurrent(1);
        config.getEngines().setConvertTimeout(Duration.ofSeconds(5));
        ImageWorkLimiter limiter = new ImageWorkLimiter(config);
        NativeProcessRunner runner = new NativeProcessRunner(config, limiter);
        CountDownLatch inHold = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> limiter.call(() -> {
            inHold.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        assertTrue(inHold.await(2, TimeUnit.SECONDS));
        String out = runner.runUnlocked(List.of("echo", "ok"), tempDir, Duration.ofSeconds(5));
        assertTrue(out.contains("ok"));
        release.countDown();
        holder.join(2000);
    }

    @Test
    void runTimesOutWhenLimiterHeld() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.getEngines().setMaxConcurrent(1);
        config.getEngines().setConvertTimeout(Duration.ofMillis(80));
        ImageWorkLimiter limiter = new ImageWorkLimiter(config);
        NativeProcessRunner runner = new NativeProcessRunner(config, limiter);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch inHold = new CountDownLatch(1);
        Thread holder = new Thread(() -> limiter.call(() -> {
            inHold.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        assertTrue(inHold.await(2, TimeUnit.SECONDS));
        BizException ex = assertThrows(BizException.class,
                () -> runner.run(List.of("echo", "ok"), tempDir, Duration.ofSeconds(1)));
        assertTrue(ex.getMessage().contains("繁忙"));
        release.countDown();
        holder.join(2000);
    }
}
