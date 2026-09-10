package com.hfwas.devops.container.service.cluster;

import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.mapper.ClusterMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic heartbeat that checks cluster health and updates status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterHeartbeatJob {

    private final ClusterMapper clusterMapper;
    private final ClusterKubernetesClientFactory clientFactory;
    private final KubeconfigCipher kubeconfigCipher;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    public void heartbeat() {
        List<ClusterEntity> clusters = clusterMapper.selectList(null);
        for (ClusterEntity cluster : clusters) {
            try {
                boolean healthy;
                try {
                    KubernetesClient client = clientFactory.getClient(cluster);
                    healthy = client.getKubernetesVersion() != null;
                } catch (Exception e) {
                    healthy = false;
                }

                String newStatus = healthy ? "Connected" : "Degraded";
                if (!newStatus.equals(cluster.getStatus())) {
                    cluster.setStatus(newStatus);
                    clusterMapper.updateById(cluster);
                    log.debug("Cluster heartbeat: id={} name={} status={}", cluster.getId(), cluster.getName(), newStatus);
                }
            } catch (Exception e) {
                log.warn("Cluster heartbeat failed for id={}: {}", cluster.getId(), e.getMessage());
                if (!"Disconnected".equals(cluster.getStatus())) {
                    cluster.setStatus("Disconnected");
                    clusterMapper.updateById(cluster);
                }
            }
        }
    }
}