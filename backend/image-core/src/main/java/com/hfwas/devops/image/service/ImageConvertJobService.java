package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertVO;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ImageConvertJobService {

    private final Map<String, ImageConvertVO> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool;

    public ImageConvertJobService(ImageProcessorConfig config) {
        int n = Math.max(1, config.getEngines().getMaxConcurrent());
        this.pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "image-convert");
            t.setDaemon(true);
            return t;
        });
    }

    public ImageConvertVO enqueue(String sessionId, Supplier<ImageConvertVO> work) {
        String jobId = Ulids.next();
        ImageConvertVO queued = ImageConvertVO.builder()
                .sessionId(sessionId)
                .jobId(jobId)
                .status("queued")
                .build();
        jobs.put(jobId, queued);
        pool.execute(() -> {
            jobs.put(jobId, ImageConvertVO.builder()
                    .sessionId(sessionId)
                    .jobId(jobId)
                    .status("running")
                    .build());
            try {
                ImageConvertVO done = work.get();
                done.setJobId(jobId);
                done.setStatus("completed");
                jobs.put(jobId, done);
            } catch (BizException e) {
                jobs.put(jobId, ImageConvertVO.builder()
                        .sessionId(sessionId)
                        .jobId(jobId)
                        .status("failed")
                        .errorMessage(e.getMessage())
                        .build());
            } catch (Exception e) {
                jobs.put(jobId, ImageConvertVO.builder()
                        .sessionId(sessionId)
                        .jobId(jobId)
                        .status("failed")
                        .errorMessage(e.getMessage() == null ? "转换失败" : e.getMessage())
                        .build());
            }
        });
        return queued;
    }

    public ImageConvertVO require(String sessionId, String jobId) {
        ImageConvertVO vo = jobs.get(jobId);
        if (vo == null || !sessionId.equals(vo.getSessionId())) {
            throw new BizException(ResultCode.NOT_FOUND, "转换任务不存在");
        }
        return vo;
    }

    public void evictSession(String sessionId) {
        jobs.entrySet().removeIf(e -> sessionId.equals(e.getValue().getSessionId()));
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
