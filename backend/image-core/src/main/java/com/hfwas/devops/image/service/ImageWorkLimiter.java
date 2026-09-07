package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 限制同时解码/转码的任务数，避免 JVM 堆上多幅 BufferedImage
 * 与 Magick 进程堆外像素缓存叠加把容器打满。
 */
@Component
public class ImageWorkLimiter {

    private final Semaphore semaphore;
    private final Duration wait;

    public ImageWorkLimiter(ImageProcessorConfig config) {
        int permits = Math.max(1, config.getEngines().getMaxConcurrent());
        this.semaphore = new Semaphore(permits);
        this.wait = config.getEngines().getConvertTimeout();
    }

    public <T> T call(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.OPERATION_FAILED, "图片处理被中断");
        }
        if (!acquired) {
            throw new BizException(ResultCode.OPERATION_FAILED, "图片处理繁忙，请稍后重试");
        }
        try {
            return work.get();
        } finally {
            semaphore.release();
        }
    }
}
