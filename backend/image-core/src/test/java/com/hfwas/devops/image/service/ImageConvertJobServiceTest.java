package com.hfwas.devops.image.service;

import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertVO;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageConvertJobServiceTest {

    @Test
    void evictMarksPendingJobFailedAndIgnoresLateCompletion() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.getEngines().setMaxConcurrent(1);
        ImageConvertJobService jobs = new ImageConvertJobService(config);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch block = new CountDownLatch(1);
        ImageConvertVO queued = jobs.enqueue("s1", () -> {
            started.countDown();
            try {
                block.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ImageConvertVO.builder().sessionId("s1").status("completed").build();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        jobs.evictSession("s1");
        ImageConvertVO failed = jobs.require("s1", queued.getJobId());
        assertEquals("failed", failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("过期"));
        block.countDown();
        Thread.sleep(80);
        ImageConvertVO stillFailed = jobs.require("s1", queued.getJobId());
        assertEquals("failed", stillFailed.getStatus());
        jobs.shutdown();
    }
}
