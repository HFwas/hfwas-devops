package com.hfwas.devops.container.service.cluster;

import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.util.NetworkUtil;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one {@link KubernetesClient} per cluster.
 * Clients are lazily created and cached. Evict when cluster config changes.
 */
@Slf4j
@Component
public class ClusterKubernetesClientFactory {

    private final KubeconfigCipher kubeconfigCipher;
    private final ConcurrentHashMap<Long, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    public ClusterKubernetesClientFactory(KubeconfigCipher kubeconfigCipher) {
        this.kubeconfigCipher = kubeconfigCipher;
    }

    /**
     * Get or create a cached client for the given cluster.
     */
    public KubernetesClient getClient(ClusterEntity cluster) {
        return clientCache.computeIfAbsent(cluster.getId(), id -> buildClient(cluster));
    }

    /**
     * Evict and close the client for the given cluster (e.g. on kubeconfig update).
     */
    public void evictClient(Long clusterId) {
        KubernetesClient client = clientCache.remove(clusterId);
        if (client != null) {
            try {
                client.close();
                log.info("KubernetesClient evicted for cluster id={}", clusterId);
            } catch (Exception e) {
                log.warn("Error closing KubernetesClient for cluster id={}: {}", clusterId, e.getMessage());
            }
        }
    }

    /**
     * Test cluster connectivity using /readyz (or equivalent).
     */
    public boolean testConnection(ClusterEntity cluster) {
        try {
            // 先检查 TCP 可达性，避免 VPN 断开时长时间等待
            String masterUrl = extractMasterUrl(cluster);
            if (masterUrl != null) {
                String unreachable = NetworkUtil.checkReachable(masterUrl);
                if (unreachable != null) {
                    log.warn("Cluster connection test failed: cluster={}, error={}", cluster.getName(), unreachable);
                    return false;
                }
            }

            KubernetesClient client = buildClient(cluster);
            boolean healthy = client.getKubernetesVersion() != null;
            client.close();
            return healthy;
        } catch (Exception e) {
            log.warn("Cluster connection test failed: cluster={}, error={}", cluster.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Extract master URL from kubeconfig for connectivity check.
     */
    private String extractMasterUrl(ClusterEntity cluster) {
        try {
            String plainKubeconfig = kubeconfigCipher.decrypt(cluster.getKubeconfig());
            Config config = Config.fromKubeconfig(plainKubeconfig);
            return config.getMasterUrl();
        } catch (Exception e) {
            log.debug("Failed to extract master URL from kubeconfig: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a new client from cluster kubeconfig.
     */
    private KubernetesClient buildClient(ClusterEntity cluster) {
        String plainKubeconfig = kubeconfigCipher.decrypt(cluster.getKubeconfig());
        Config config = Config.fromKubeconfig(plainKubeconfig);
        // Fabric8 file().upload() waits on this; keep it under typical gateway timeouts
        // for hung execs, but long enough for the 100 MB upload limit.
        config.setUploadRequestTimeout(120_000);
        KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
        log.info("KubernetesClient created for cluster id={} name={}", cluster.getId(), cluster.getName());
        return client;
    }

    @PreDestroy
    public void shutdown() {
        clientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        });
        clientCache.clear();
        log.info("All KubernetesClients closed on shutdown");
    }
}