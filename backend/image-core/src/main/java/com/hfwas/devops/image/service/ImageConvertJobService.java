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
        pool.execute(() -> runJob(sessionId, jobId, work));
        return queued;
    }

    private void runJob(String sessionId, String jobId, Supplier<ImageConvertVO> work) {
        boolean proceed = markRunning(jobId, sessionId);
        if (!proceed) {
            return;
        }
        try {
            ImageConvertVO done = work.get();
            jobs.compute(jobId, (id, current) -> {
                if (isFailed(current)) {
                    return current;
                }
                done.setJobId(jobId);
                done.setStatus("completed");
                return done;
            });
        } catch (BizException e) {
            fail(jobId, sessionId, e.getMessage());
        } catch (Exception e) {
            fail(jobId, sessionId, e.getMessage() == null ? "转换失败" : e.getMessage());
        }
    }

    private boolean markRunning(String jobId, String sessionId) {
        boolean[] proceed = {true};
        jobs.compute(jobId, (id, current) -> {
            if (isFailed(current)) {
                proceed[0] = false;
                return current;
            }
            return ImageConvertVO.builder()
                    .sessionId(sessionId)
                    .jobId(jobId)
                    .status("running")
                    .build();
        });
        return proceed[0];
    }

    private void fail(String jobId, String sessionId, String message) {
        jobs.compute(jobId, (id, current) -> {
            if (isFailed(current)) {
                return current;
            }
            return ImageConvertVO.builder()
                    .sessionId(sessionId)
                    .jobId(jobId)
                    .status("failed")
                    .errorMessage(message)
                    .build();
        });
    }

    public ImageConvertVO require(String sessionId, String jobId) {
        ImageConvertVO vo = jobs.get(jobId);
        if (vo == null || !sessionId.equals(vo.getSessionId())) {
            throw new BizException(ResultCode.NOT_FOUND, "转换任务不存在");
        }
        return vo;
    }

    public void evictSession(String sessionId) {
        jobs.replaceAll((id, vo) -> {
            if (!sessionId.equals(vo.getSessionId()) || isFailed(vo)) {
                return vo;
            }
            if ("completed".equals(vo.getStatus())) {
                return vo;
            }
            return ImageConvertVO.builder()
                    .sessionId(sessionId)
                    .jobId(vo.getJobId())
                    .status("failed")
                    .errorMessage("会话不存在或已过期")
                    .build();
        });
    }

    private static boolean isFailed(ImageConvertVO vo) {
        return vo != null && "failed".equals(vo.getStatus());
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
