package com.hfwas.devops.container.service.cluster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.mapper.ClusterMapper;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterMapper clusterMapper;
    private final KubeconfigCipher kubeconfigCipher;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    /**
     * Register a new cluster. Kubeconfig is encrypted before persistence.
     */
    @Transactional
    public Long create(ClusterEntity entity) {
        // tenant + name uniqueness is enforced by DB UNIQUE constraint
        entity.setKubeconfig(kubeconfigCipher.encrypt(entity.getKubeconfig()));
        if (entity.getMode() == null) entity.setMode("proxy");
        if (entity.getStatus() == null) entity.setStatus("Unknown");
        if (entity.getLabels() == null) entity.setLabels("{}");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        clusterMapper.insert(entity);
        return entity.getId();
    }

    /**
     * Paginated list filtered by tenant.
     */
    public IPage<ClusterEntity> page(int pageNo, int pageSize, Long tenantId) {
        LambdaQueryWrapper<ClusterEntity> wrapper = new LambdaQueryWrapper<ClusterEntity>()
                .eq(tenantId != null, ClusterEntity::getTenantId, tenantId)
                .orderByDesc(ClusterEntity::getUpdatedAt);
        return clusterMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    /**
     * Get cluster by id, visibility-checked against tenant.
     */
    public ClusterEntity getById(Long id, Long tenantId) {
        ClusterEntity entity = clusterMapper.selectById(id);
        if (entity == null || (tenantId != null && !tenantId.equals(entity.getTenantId()))) {
            throw new BizException(ContainerErrorCode.CLUSTER_FORBIDDEN);
        }
        return entity;
    }

    /**
     * Get cluster by id without tenant check (internal use, e.g. heartbeat).
     */
    public ClusterEntity getByIdInternal(Long id) {
        ClusterEntity entity = clusterMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ContainerErrorCode.CLUSTER_NOT_FOUND);
        }
        return entity;
    }

    /**
     * Update cluster metadata. If kubeconfig changed, re-encrypt and evict client cache.
     */
    @Transactional
    public void update(ClusterEntity entity, Long tenantId) {
        ClusterEntity existing = getById(entity.getId(), tenantId);
        existing.setAlias(entity.getAlias());
        existing.setProvider(entity.getProvider());
        existing.setLabels(entity.getLabels());
        if (entity.getKubeconfig() != null && !entity.getKubeconfig().isBlank()) {
            existing.setKubeconfig(kubeconfigCipher.encrypt(entity.getKubeconfig()));
            clientFactory.evictClient(entity.getId());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        clusterMapper.updateById(existing);
    }

    /**
     * Delete cluster, evict client cache.
     */
    @Transactional
    public void delete(Long id, Long tenantId) {
        getById(id, tenantId); // visibility check
        clientFactory.evictClient(id);
        clusterMapper.deleteById(id);
    }

    /**
     * Test cluster connectivity.
     */
    public boolean testConnection(Long id, Long tenantId) {
        ClusterEntity entity = getById(id, tenantId);
        return clientFactory.testConnection(entity);
    }

    /**
     * Cluster resource statistics (from K8s API).
     */
    public ClusterStats stats(Long id, Long tenantId) {
        ClusterEntity entity = getById(id, tenantId);
        int nodeCount = 0;
        int podCount = 0;
        double cpuTotal = 0;
        long memoryTotal = 0L;
        try {
            KubernetesClient client = clientFactory.getClient(entity);

            // Node count
            List<Node> nodes = client.nodes().list().getItems();
            nodeCount = nodes.size();

            // Pod count
            List<Pod> pods = client.pods().inAnyNamespace().list().getItems();
            podCount = pods.size();

            // CPU / Memory totals from node allocatable
            for (Node node : nodes) {
                Map<String, io.fabric8.kubernetes.api.model.Quantity> allocatable = node.getStatus().getAllocatable();
                if (allocatable != null) {
                    io.fabric8.kubernetes.api.model.Quantity cpu = allocatable.get("cpu");
                    io.fabric8.kubernetes.api.model.Quantity mem = allocatable.get("memory");
                    if (cpu != null) cpuTotal += cpu.getNumericalAmount().doubleValue();
                    if (mem != null) memoryTotal += mem.getNumericalAmount().longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch cluster stats for id={}: {}", id, e.getMessage());
        }
        return new ClusterStats(nodeCount, podCount, cpuTotal, memoryTotal);
    }

    /**
     * List all cluster IDs for heartbeat job (across tenants).
     */
    public List<Long> listAllClusterIds() {
        return clusterMapper.selectList(null).stream()
                .map(ClusterEntity::getId)
                .toList();
    }

    /**
     * Update cluster status.
     */
    public void updateStatus(Long id, String status) {
        ClusterEntity entity = clusterMapper.selectById(id);
        if (entity != null) {
            entity.setStatus(status);
            entity.setUpdatedAt(LocalDateTime.now());
            clusterMapper.updateById(entity);
        }
    }

    /**
     * Verify cluster is visible to the given tenant; throws if not.
     */
    public void requireVisible(Long clusterId, Long tenantId) {
        getById(clusterId, tenantId);
    }

    // ---- inner: stats VO ----

    public record ClusterStats(int nodeCount, int podCount, double cpuTotal, long memoryTotal) {
        ClusterStats() {
            this(0, 0, 0, 0L);
        }
    }
}