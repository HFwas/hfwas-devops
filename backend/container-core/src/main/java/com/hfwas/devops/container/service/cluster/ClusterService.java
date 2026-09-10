package com.hfwas.devops.container.service.cluster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.ClusterComponentVO;
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
import java.util.ArrayList;
import java.util.HashMap;
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
        if (entity.getLabels() == null) entity.setLabels("{}");
        entity.setStatus("Unknown");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        clusterMapper.insert(entity);

        // Immediately test connectivity and set realistic status
        boolean connected = clientFactory.testConnection(entity);
        String newStatus = connected ? "Connected" : "Disconnected";
        entity.setStatus(newStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        clusterMapper.updateById(entity);

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
     * Get cluster component versions and status.
     */
    public ClusterComponentVO getComponents(Long id, Long tenantId) {
        ClusterEntity entity = getById(id, tenantId);
        KubernetesClient client = clientFactory.getClient(entity);

        ClusterComponentVO vo = new ClusterComponentVO();

        // Kubernetes version
        try {
            var versionInfo = client.getKubernetesVersion();
            vo.setKubernetesVersion(versionInfo != null
                    ? versionInfo.getMajor() + "." + versionInfo.getMinor() : "unknown");
        } catch (Exception e) {
            vo.setKubernetesVersion("unknown");
        }

        // Nodes with component versions
        List<ClusterComponentVO.NodeComponentVO> nodes = new ArrayList<>();
        try {
            var nodeList = client.nodes().list().getItems();
            vo.setNodeCount(nodeList.size());
            for (var n : nodeList) {
                var meta = n.getMetadata();
                var nv = new ClusterComponentVO.NodeComponentVO();
                nv.setName(meta.getName());
                nv.setKubeletVersion(n.getStatus().getNodeInfo() != null
                        ? n.getStatus().getNodeInfo().getKubeletVersion() : "-");
                nv.setContainerRuntime(n.getStatus().getNodeInfo() != null
                        ? n.getStatus().getNodeInfo().getContainerRuntimeVersion() : "-");
                nv.setOsImage(n.getStatus().getNodeInfo() != null
                        ? n.getStatus().getNodeInfo().getOsImage() : "-");
                nv.setKernelVersion(n.getStatus().getNodeInfo() != null
                        ? n.getStatus().getNodeInfo().getKernelVersion() : "-");
                nv.setArchitecture(n.getStatus().getNodeInfo() != null
                        ? n.getStatus().getNodeInfo().getArchitecture() : "-");

                // Determine node status
                String status = "Unknown";
                if (n.getStatus() != null && n.getStatus().getConditions() != null) {
                    for (var c : n.getStatus().getConditions()) {
                        if ("Ready".equals(c.getType())) {
                            status = "True".equals(c.getStatus()) ? "Ready" : "NotReady";
                            break;
                        }
                    }
                }
                nv.setStatus(status);
                nodes.add(nv);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch node component info: {}", e.getMessage());
        }
        vo.setNodes(nodes);

        // System components from kube-system namespace (deployments, daemonsets)
        List<ClusterComponentVO.SystemComponentVO> systemComponents = new ArrayList<>();
        try {
            // CoreDNS
            var corednsDeploy = client.apps().deployments().inNamespace("kube-system")
                    .withName("coredns").get();
            if (corednsDeploy != null) {
                var sv = new ClusterComponentVO.SystemComponentVO();
                sv.setName("CoreDNS");
                sv.setNamespace("kube-system");
                sv.setDesiredReplicas(corednsDeploy.getSpec().getReplicas() != null
                        ? corednsDeploy.getSpec().getReplicas() : 0);
                sv.setReadyReplicas(corednsDeploy.getStatus() != null
                        ? (corednsDeploy.getStatus().getReadyReplicas() != null
                            ? corednsDeploy.getStatus().getReadyReplicas() : 0) : 0);
                sv.setStatus(sv.getReadyReplicas() >= sv.getDesiredReplicas() ? "Healthy" : "Degraded");
                sv.setVersion(corednsDeploy.getMetadata().getLabels() != null
                        ? corednsDeploy.getMetadata().getLabels().getOrDefault("k8s-app", "-") : "-");
                systemComponents.add(sv);
            }

            // kube-proxy (DaemonSet)
            var kubeProxyDs = client.apps().daemonSets().inNamespace("kube-system")
                    .withName("kube-proxy").get();
            if (kubeProxyDs != null && kubeProxyDs.getStatus() != null) {
                var sv = new ClusterComponentVO.SystemComponentVO();
                sv.setName("kube-proxy");
                sv.setNamespace("kube-system");
                sv.setDesiredReplicas(kubeProxyDs.getStatus().getDesiredNumberScheduled() != null
                        ? kubeProxyDs.getStatus().getDesiredNumberScheduled() : 0);
                sv.setReadyReplicas(kubeProxyDs.getStatus().getNumberReady() != null
                        ? kubeProxyDs.getStatus().getNumberReady() : 0);
                sv.setStatus(sv.getReadyReplicas() >= sv.getDesiredReplicas() ? "Healthy" : "Degraded");
                systemComponents.add(sv);
            }

            // Also try to get other common components
            String[][] commonComponents = {
                {"kube-system", "etcd"},
                {"kube-system", "kube-apiserver"},
                {"kube-system", "kube-controller-manager"},
                {"kube-system", "kube-scheduler"},
            };
            for (String[] comp : commonComponents) {
                var podList = client.pods().inNamespace(comp[0])
                        .withLabel("component", comp[1]).list().getItems();
                if (podList.isEmpty()) {
                    // Try with app label
                    podList = client.pods().inNamespace(comp[0])
                            .withLabel("app", comp[1]).list().getItems();
                }
                if (!podList.isEmpty()) {
                    var sv = new ClusterComponentVO.SystemComponentVO();
                    sv.setName(comp[1]);
                    sv.setNamespace(comp[0]);
                    long ready = podList.stream()
                            .filter(p -> p.getStatus() != null)
                            .filter(p -> p.getStatus().getContainerStatuses() != null)
                            .filter(p -> p.getStatus().getContainerStatuses().stream()
                                    .anyMatch(cs -> cs.getReady()))
                            .count();
                    sv.setReadyReplicas((int) ready);
                    sv.setDesiredReplicas(podList.size());
                    sv.setStatus(ready == podList.size() ? "Healthy" : "Degraded");
                    systemComponents.add(sv);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch system component info: {}", e.getMessage());
        }
        vo.setSystemComponents(systemComponents);

        return vo;
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