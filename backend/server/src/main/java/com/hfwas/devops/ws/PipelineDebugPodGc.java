package com.hfwas.devops.ws;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 调试 Pod 垃圾回收器。
 * <p>
 * 启动时 + 每 30 分钟清理一次过期的 debug Pod（自动创建用于调试的临时 Pod）。
 * debug Pod 带有标签 app=pipeline-debug，超过 1 小时未被使用则被删除。
 * 当 KubernetesClient 不可用（未配置集群）时，GC 静默跳过。
 */
@Slf4j
@Component
public class PipelineDebugPodGc {

    private static final long MAX_AGE_HOURS = 1;
    private static final String GC_LABEL = "app";
    private static final String GC_LABEL_VALUE = "pipeline-debug";
    private static final String GC_ANNOTATION_CREATED = "pipeline-debug-created-at";

    private final ObjectProvider<KubernetesClient> kubernetesClients;
    private final String namespace;

    public PipelineDebugPodGc(ObjectProvider<KubernetesClient> kubernetesClients,
                              @Value("${pipeline.namespace:hfwas-pipeline}") String namespace) {
        this.kubernetesClients = kubernetesClients;
        this.namespace = namespace;
    }

    /**
     * 启动时执行一次 GC
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void gcOnStartup() {
        runGc();
    }

    /**
     * 每 30 分钟定期清理
     */
    @Scheduled(fixedRate = 1_800_000)
    public void gcPeriodic() {
        runGc();
    }

    private void runGc() {
        KubernetesClient client = kubernetesClients.getIfAvailable();
        if (client == null) {
            log.debug("KubernetesClient 不可用，跳过 debug Pod GC");
            return;
        }

        try {
            List<Pod> debugPods = client.pods().inNamespace(namespace)
                    .withLabels(Map.of(GC_LABEL, GC_LABEL_VALUE))
                    .list()
                    .getItems();

            if (debugPods.isEmpty()) {
                return;
            }

            Instant cutoff = Instant.now().minus(MAX_AGE_HOURS, ChronoUnit.HOURS);
            int deleted = 0;

            for (Pod pod : debugPods) {
                if (isExpired(pod, cutoff)) {
                    String podName = pod.getMetadata().getName();
                    try {
                        client.pods().inNamespace(namespace).withName(podName).delete();
                        log.info("已清理过期 debug Pod: {}/{}", namespace, podName);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("清理 debug Pod 失败: {}/{} - {}", namespace, podName, e.getMessage());
                    }
                }
            }

            if (deleted > 0) {
                log.info("Debug Pod GC 完成: 清理 {} 个过期 Pod", deleted);
            }
        } catch (Exception e) {
            log.warn("Debug Pod GC 执行异常: {}", e.getMessage());
        }
    }

    /**
     * 判断 debug Pod 是否过期。
     * 优先看 Pod 的 creationTimestamp，兜底看自定义 annotation。
     */
    private boolean isExpired(Pod pod, Instant cutoff) {
        if (pod.getMetadata() == null) return false;

        // 用 Pod 的 creationTimestamp
        if (pod.getMetadata().getCreationTimestamp() != null) {
            try {
                Instant created = Instant.parse(pod.getMetadata().getCreationTimestamp());
                return created.isBefore(cutoff);
            } catch (Exception ignored) {}
        }

        // 兜底：自定义 annotation
        if (pod.getMetadata().getAnnotations() != null) {
            String ts = pod.getMetadata().getAnnotations().get(GC_ANNOTATION_CREATED);
            if (ts != null) {
                try {
                    Instant created = Instant.parse(ts);
                    return created.isBefore(cutoff);
                } catch (Exception ignored) {}
            }
        }

        // 无法确定创建时间，保守处理 —— 不过期
        return false;
    }
}