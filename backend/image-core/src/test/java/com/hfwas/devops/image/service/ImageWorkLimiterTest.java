package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageWorkLimiterTest {

    @Test
    void secondCallerWaitsUntilPermitReleased() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.getEngines().setMaxConcurrent(1);
        config.getEngines().setConvertTimeout(Duration.ofSeconds(5));
        ImageWorkLimiter limiter = new ImageWorkLimiter(config);
        CountDownLatch inFirst = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondStarted = new AtomicBoolean(false);
        Thread first = new Thread(() -> limiter.call(() -> {
            inFirst.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        }));
        first.start();
        assertTrue(inFirst.await(2, TimeUnit.SECONDS));
        Thread second = new Thread(() -> limiter.call(() -> {
            secondStarted.set(true);
            return 2;
        }));
        second.start();
        Thread.sleep(80);
        assertFalse(secondStarted.get());
        releaseFirst.countDown();
        second.join(2000);
        assertTrue(secondStarted.get());
        first.join(2000);
    }

    @Test
    void throwsBusyWhenPermitNotReleasedBeforeTimeout() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.getEngines().setMaxConcurrent(1);
        config.getEngines().setConvertTimeout(Duration.ofMillis(80));
        ImageWorkLimiter limiter = new ImageWorkLimiter(config);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch inHold = new CountDownLatch(1);
        Thread holder = new Thread(() -> limiter.call(() -> {
            inHold.countDown();
            try {
                held.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        }));
        holder.start();
        assertTrue(inHold.await(2, TimeUnit.SECONDS));
        BizException ex = assertThrows(BizException.class, () -> limiter.call(() -> 2));
        assertTrue(ex.getMessage().contains("繁忙"));
        held.countDown();
        holder.join(2000);
    }
}
