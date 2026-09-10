package com.hfwas.devops.container.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.*;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    // ==================== Namespace ====================

    public List<NamespaceVO> listNamespaces(Long clusterId, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        return client.namespaces().list().getItems().stream()
                .map(this::toNamespaceVO)
                .collect(Collectors.toList());
    }

    // ==================== Pod ====================

    public IPage<PodSummaryVO> listPods(Long clusterId, String namespace, String keyword,
                                         int pageNo, int pageSize, Long tenantId, String labels) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<Pod> allPods;
        if (labels != null && !labels.isBlank()) {
            // Use label selector for deployment-owned pods
            Map<String, String> labelMap = parseLabels(labels);
            allPods = (namespace != null && !namespace.isBlank())
                    ? client.pods().inNamespace(namespace).withLabels(labelMap).list().getItems()
                    : client.pods().inAnyNamespace().withLabels(labelMap).list().getItems();
        } else {
            allPods = (namespace != null && !namespace.isBlank())
                    ? client.pods().inNamespace(namespace).list().getItems()
                    : client.pods().inAnyNamespace().list().getItems();
        }

        // keyword filter
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            allPods = allPods.stream()
                    .filter(p -> p.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        // Sort by creation timestamp descending
        allPods.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        // Manual pagination
        int total = allPods.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<PodSummaryVO> records = from < total
                ? allPods.subList(from, to).stream().map(this::toPodSummary).collect(Collectors.toList())
                : List.of();

        Page<PodSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public PodDetailVO getPod(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        if (pod == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        return toPodDetail(pod);
    }

    public String getPodYaml(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        if (pod == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pod);
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    public String getPodLogs(Long clusterId, String namespace, String name,
                              String container, Integer tailLines, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        // Build pod operation chain
        var baseOp = client.pods().inNamespace(namespace).withName(name);
        // Note: inContainer() changes the return type, so we handle container separately
        // by using the fully-qualified log retrieval API
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (container != null && !container.isBlank()) {
                if (tailLines != null && tailLines > 0) {
                    LogWatch watch = baseOp.inContainer(container).tailingLines(tailLines).watchLog(baos);
                    try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    watch.close();
                } else {
                    LogWatch watch = baseOp.inContainer(container).watchLog(baos);
                    try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    watch.close();
                }
            } else {
                if (tailLines != null && tailLines > 0) {
                    LogWatch watch = baseOp.tailingLines(tailLines).watchLog(baos);
                    try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    watch.close();
                } else {
                    LogWatch watch = baseOp.watchLog(baos);
                    try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    watch.close();
                }
            }
            return baos.toString();
        } catch (Exception e) {
            log.warn("Failed to get pod logs: {}/{} - {}", namespace, name, e.getMessage());
            return "";
        }
    }

    public void deletePod(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        client.pods().inNamespace(namespace).withName(name).delete();
    }

    // ==================== Deployment ====================

    public IPage<DeploymentSummaryVO> listDeployments(Long clusterId, String namespace, String keyword,
                                                       int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<Deployment> all = (namespace != null && !namespace.isBlank())
                ? client.apps().deployments().inNamespace(namespace).list().getItems()
                : client.apps().deployments().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(d -> d.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<DeploymentSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toDeploymentSummary).collect(Collectors.toList())
                : List.of();

        Page<DeploymentSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public DeploymentDetailVO getDeployment(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        Deployment deploy = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deploy == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDeploymentDetail(deploy);
    }

    public String getDeploymentYaml(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        Deployment deploy = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deploy == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deploy);
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    public void scaleDeployment(Long clusterId, String namespace, String name, int replicas, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        client.apps().deployments().inNamespace(namespace).withName(name).scale(replicas);
    }

    public void restartDeployment(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        String patchJson = "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"kubectl.kubernetes.io/restartedAt\":\""
                + java.time.Instant.now().toString() + "\"}}}}}";
        client.apps().deployments().inNamespace(namespace).withName(name)
                .patch(PatchContext.of(PatchType.JSON), patchJson);
    }

    public void deleteDeployment(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        client.apps().deployments().inNamespace(namespace).withName(name).delete();
    }

    public void updateDeploymentYaml(Long clusterId, String namespace, String name, String yamlBody, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        try {
            Deployment deploy = objectMapper.readValue(yamlBody, Deployment.class);
            client.apps().deployments().inNamespace(namespace).resource(deploy).update();
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    // ==================== Service ====================

    public IPage<ServiceSummaryVO> listServices(Long clusterId, String namespace, String keyword,
                                                 int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<io.fabric8.kubernetes.api.model.Service> all = (namespace != null && !namespace.isBlank())
                ? client.services().inNamespace(namespace).list().getItems()
                : client.services().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(s -> s.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<ServiceSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toServiceSummary).collect(Collectors.toList())
                : List.of();

        Page<ServiceSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public ServiceDetailVO getService(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        io.fabric8.kubernetes.api.model.Service svc = client.services().inNamespace(namespace).withName(name).get();
        if (svc == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        return toServiceDetail(svc);
    }

    public String getServiceYaml(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        io.fabric8.kubernetes.api.model.Service svc = client.services().inNamespace(namespace).withName(name).get();
        if (svc == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(svc);
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    // ==================== StatefulSet ====================

    public IPage<StatefulSetSummaryVO> listStatefulSets(Long clusterId, String namespace, String keyword,
                                                        int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<StatefulSet> all = (namespace != null && !namespace.isBlank())
                ? client.apps().statefulSets().inNamespace(namespace).list().getItems()
                : client.apps().statefulSets().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(s -> s.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<StatefulSetSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toStatefulSetSummary).collect(Collectors.toList())
                : List.of();

        Page<StatefulSetSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public void deleteStatefulSet(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        client.apps().statefulSets().inNamespace(namespace).withName(name).delete();
    }

    public String getStatefulSetYaml(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        StatefulSet sts = client.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (sts == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sts);
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    public void updateStatefulSetYaml(Long clusterId, String namespace, String name, String yamlBody, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        try {
            StatefulSet sts = objectMapper.readValue(yamlBody, StatefulSet.class);
            client.apps().statefulSets().inNamespace(namespace).resource(sts).update();
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    // ==================== PersistentVolumeClaim ====================

    public IPage<PvcSummaryVO> listPersistentVolumeClaims(Long clusterId, String namespace, String keyword,
                                                           int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<PersistentVolumeClaim> all = (namespace != null && !namespace.isBlank())
                ? client.persistentVolumeClaims().inNamespace(namespace).list().getItems()
                : client.persistentVolumeClaims().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(p -> p.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<PvcSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toPvcSummary).collect(Collectors.toList())
                : List.of();

        Page<PvcSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public void deletePersistentVolumeClaim(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        client.persistentVolumeClaims().inNamespace(namespace).withName(name).delete();
    }

    // ==================== Secret ====================

    public IPage<SecretSummaryVO> listSecrets(Long clusterId, String namespace, String keyword,
                                               int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<Secret> all = (namespace != null && !namespace.isBlank())
                ? client.secrets().inNamespace(namespace).list().getItems()
                : client.secrets().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(s -> s.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<SecretSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toSecretSummary).collect(Collectors.toList())
                : List.of();

        Page<SecretSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public void deleteSecret(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        client.secrets().inNamespace(namespace).withName(name).delete();
    }

    // ==================== ConfigMap ====================

    public IPage<ConfigMapSummaryVO> listConfigMaps(Long clusterId, String namespace, String keyword,
                                                     int pageNo, int pageSize, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<ConfigMap> all = (namespace != null && !namespace.isBlank())
                ? client.configMaps().inNamespace(namespace).list().getItems()
                : client.configMaps().inAnyNamespace().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            all = all.stream()
                    .filter(c -> c.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        all.sort((a, b) -> {
            String t1 = a.getMetadata().getCreationTimestamp();
            String t2 = b.getMetadata().getCreationTimestamp();
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        int total = all.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<ConfigMapSummaryVO> records = from < total
                ? all.subList(from, to).stream().map(this::toConfigMapSummary).collect(Collectors.toList())
                : List.of();

        Page<ConfigMapSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public void deleteConfigMap(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        client.configMaps().inNamespace(namespace).withName(name).delete();
    }

    public String getConfigMapYaml(Long clusterId, String namespace, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        ConfigMap cm = client.configMaps().inNamespace(namespace).withName(name).get();
        if (cm == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cm);
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    public void updateConfigMapYaml(Long clusterId, String namespace, String name, String yamlBody, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);
        try {
            ConfigMap cm = objectMapper.readValue(yamlBody, ConfigMap.class);
            client.configMaps().inNamespace(namespace).resource(cm).update();
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED);
        }
    }

    // ==================== StatefulSet ====================

    public List<EventVO> listEvents(Long clusterId, String namespace, String uid, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);
        requireNamespace(namespace);

        List<io.fabric8.kubernetes.api.model.Event> events =
                client.v1().events().inNamespace(namespace).list().getItems();

        if (uid != null && !uid.isBlank()) {
            events = events.stream()
                    .filter(e -> e.getInvolvedObject() != null && uid.equals(e.getInvolvedObject().getUid()))
                    .collect(Collectors.toList());
        }

        events.sort((a, b) -> {
            if (a.getLastTimestamp() == null) return 1;
            if (b.getLastTimestamp() == null) return -1;
            return b.getLastTimestamp().compareTo(a.getLastTimestamp());
        });

        return events.stream().map(this::toEventVO).collect(Collectors.toList());
    }

    // ==================== VO mapping ====================

    private NamespaceVO toNamespaceVO(Namespace ns) {
        NamespaceVO vo = new NamespaceVO();
        vo.setName(ns.getMetadata().getName());
        vo.setUid(ns.getMetadata().getUid());
        vo.setCreationTimestamp(toLocalDateTime(ns.getMetadata().getCreationTimestamp()));
        if (ns.getStatus() != null) {
            vo.setPhase(ns.getStatus().getPhase());
            vo.setStatus(ns.getStatus().getPhase());
        }
        return vo;
    }

    private PodSummaryVO toPodSummary(Pod pod) {
        PodSummaryVO vo = new PodSummaryVO();
        ObjectMeta meta = pod.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        if (pod.getStatus() != null) {
            String phase = pod.getStatus().getPhase();
            vo.setStatus(phase != null ? phase : "Unknown");
            if (pod.getSpec() != null) {
                vo.setNodeName(pod.getSpec().getNodeName());
            }
            if (pod.getStatus().getPodIP() != null) {
                vo.setPodIP(pod.getStatus().getPodIP());
            }
            if (pod.getStatus().getContainerStatuses() != null) {
                int total = pod.getStatus().getContainerStatuses().size();
                long ready = pod.getStatus().getContainerStatuses().stream().filter(ContainerStatus::getReady).count();
                int restarts = pod.getStatus().getContainerStatuses().stream()
                        .mapToInt(cs -> cs.getRestartCount() != null ? cs.getRestartCount() : 0).sum();
                vo.setContainerCount(total);
                vo.setReadyContainers((int) ready);
                vo.setRestarts(restarts);
            }
        }
        return vo;
    }

    private PodDetailVO toPodDetail(Pod pod) {
        PodDetailVO vo = new PodDetailVO();
        PodSummaryVO summary = toPodSummary(pod);
        vo.setName(summary.getName());
        vo.setNamespace(summary.getNamespace());
        vo.setStatus(summary.getStatus());
        vo.setNodeName(summary.getNodeName());
        vo.setPodIP(summary.getPodIP());
        vo.setContainerCount(summary.getContainerCount());
        vo.setReadyContainers(summary.getReadyContainers());
        vo.setRestarts(summary.getRestarts());
        vo.setAge(summary.getAge());
        vo.setCreationTimestamp(summary.getCreationTimestamp());

        ObjectMeta meta = pod.getMetadata();
        vo.setUid(meta.getUid());
        vo.setLabels(meta.getLabels());
        vo.setAnnotations(meta.getAnnotations());

        if (meta.getOwnerReferences() != null && !meta.getOwnerReferences().isEmpty()) {
            vo.setOwnerReference(meta.getOwnerReferences().getFirst().getName());
        }
        if (pod.getStatus() != null) {
            vo.setQosClass(pod.getStatus().getQosClass());
        }

        // Containers
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            List<ContainerVO> containers = pod.getSpec().getContainers().stream().map(c -> {
                ContainerVO cv = new ContainerVO();
                cv.setName(c.getName());
                cv.setImage(c.getImage());
                return cv;
            }).collect(Collectors.toList());

            if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                for (ContainerVO cv : containers) {
                    pod.getStatus().getContainerStatuses().stream()
                            .filter(cs -> cs.getName().equals(cv.getName()))
                            .findFirst().ifPresent(cs -> {
                                cv.setReady(cs.getReady());
                                cv.setRestartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0);
                                if (cs.getState() != null) {
                                    if (cs.getState().getRunning() != null) cv.setState("running");
                                    else if (cs.getState().getTerminated() != null) {
                                        cv.setState("terminated");
                                        cv.setExitCode(cs.getState().getTerminated().getExitCode());
                                    } else if (cs.getState().getWaiting() != null) cv.setState("waiting");
                                }
                            });
                }
            }
            vo.setContainers(containers);
        }

        // Conditions
        if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
            vo.setConditions(pod.getStatus().getConditions().stream().map(c -> {
                PodConditionVO pcv = new PodConditionVO();
                pcv.setType(c.getType());
                pcv.setStatus(c.getStatus());
                pcv.setReason(c.getReason());
                pcv.setMessage(c.getMessage());
                return pcv;
            }).collect(Collectors.toList()));
        }

        return vo;
    }

    private DeploymentSummaryVO toDeploymentSummary(Deployment deploy) {
        DeploymentSummaryVO vo = new DeploymentSummaryVO();
        ObjectMeta meta = deploy.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        DeploymentSpec spec = deploy.getSpec();
        if (spec != null) {
            vo.setDesiredReplicas(spec.getReplicas() != null ? spec.getReplicas() : 0);
            if (spec.getStrategy() != null) {
                vo.setStrategy(spec.getStrategy().getType());
            }
        }

        if (deploy.getStatus() != null) {
            vo.setReadyReplicas(deploy.getStatus().getReadyReplicas() != null ? deploy.getStatus().getReadyReplicas() : 0);
            vo.setAvailableReplicas(deploy.getStatus().getAvailableReplicas() != null ? deploy.getStatus().getAvailableReplicas() : 0);
        }

        return vo;
    }

    private DeploymentDetailVO toDeploymentDetail(Deployment deploy) {
        DeploymentDetailVO vo = new DeploymentDetailVO();
        DeploymentSummaryVO summary = toDeploymentSummary(deploy);
        vo.setName(summary.getName());
        vo.setNamespace(summary.getNamespace());
        vo.setDesiredReplicas(summary.getDesiredReplicas());
        vo.setReadyReplicas(summary.getReadyReplicas());
        vo.setAvailableReplicas(summary.getAvailableReplicas());
        vo.setStrategy(summary.getStrategy());
        vo.setAge(summary.getAge());
        vo.setCreationTimestamp(summary.getCreationTimestamp());

        vo.setUid(deploy.getMetadata().getUid());
        vo.setLabels(deploy.getMetadata().getLabels());
        vo.setAnnotations(deploy.getMetadata().getAnnotations());

        if (deploy.getSpec() != null) {
            if (deploy.getSpec().getSelector() != null) {
                vo.setSelector(deploy.getSpec().getSelector().getMatchLabels() != null
                        ? deploy.getSpec().getSelector().getMatchLabels().toString() : "");
            }
            if (deploy.getSpec().getRevisionHistoryLimit() != null) {
                vo.setRevisionHistoryLimit(String.valueOf(deploy.getSpec().getRevisionHistoryLimit()));
            }
            if (deploy.getSpec().getMinReadySeconds() != null) {
                vo.setMinReadySeconds(String.valueOf(deploy.getSpec().getMinReadySeconds()));
            }

            if (deploy.getSpec().getTemplate() != null && deploy.getSpec().getTemplate().getSpec() != null) {
                var podSpec = deploy.getSpec().getTemplate().getSpec();

                // Status
                if (deploy.getStatus() != null) {
                    StringBuilder status = new StringBuilder();
                    if (deploy.getStatus().getReplicas() != null) status.append("replicas=").append(deploy.getStatus().getReplicas()).append(" ");
                    if (deploy.getStatus().getUpdatedReplicas() != null) status.append("updated=").append(deploy.getStatus().getUpdatedReplicas()).append(" ");
                    if (deploy.getStatus().getReadyReplicas() != null) status.append("ready=").append(deploy.getStatus().getReadyReplicas()).append(" ");
                    if (deploy.getStatus().getAvailableReplicas() != null) status.append("available=").append(deploy.getStatus().getAvailableReplicas()).append(" ");
                    if (deploy.getStatus().getUnavailableReplicas() != null) status.append("unavailable=").append(deploy.getStatus().getUnavailableReplicas());
                    vo.setStatus(status.toString().trim());
                }

                // Containers with resources
                if (!podSpec.getContainers().isEmpty()) {
                    vo.setImage(podSpec.getContainers().getFirst().getImage());
                    List<DeploymentDetailVO.ContainerResourceVO> containers = podSpec.getContainers().stream()
                            .map(this::toContainerResource)
                            .collect(Collectors.toList());
                    vo.setContainers(containers);
                }

                // Volumes
                if (podSpec.getVolumes() != null) {
                    List<DeploymentDetailVO.VolumeMountVO> volumes = podSpec.getVolumes().stream()
                            .map(v -> {
                                DeploymentDetailVO.VolumeMountVO vm = new DeploymentDetailVO.VolumeMountVO();
                                vm.setName(v.getName());
                                if (v.getConfigMap() != null) vm.setVolumeType("ConfigMap");
                                else if (v.getSecret() != null) vm.setVolumeType("Secret");
                                else if (v.getPersistentVolumeClaim() != null) vm.setVolumeType("PVC");
                                else if (v.getEmptyDir() != null) vm.setVolumeType("EmptyDir");
                                else if (v.getHostPath() != null) vm.setVolumeType("HostPath");
                                else vm.setVolumeType("Other");
                                return vm;
                            })
                            .collect(Collectors.toList());
                    vo.setVolumes(volumes);
                }
            }
        }

        return vo;
    }

    private DeploymentDetailVO.ContainerResourceVO toContainerResource(Container container) {
        DeploymentDetailVO.ContainerResourceVO vo = new DeploymentDetailVO.ContainerResourceVO();
        vo.setName(container.getName());
        vo.setImage(container.getImage());
        if (container.getResources() != null) {
            if (container.getResources().getRequests() != null) {
                var req = container.getResources().getRequests();
                if (req.get("cpu") != null) vo.setCpuRequest(req.get("cpu").getAmount() + req.get("cpu").getFormat());
                if (req.get("memory") != null) vo.setMemRequest(req.get("memory").getAmount() + req.get("memory").getFormat());
            }
            if (container.getResources().getLimits() != null) {
                var lim = container.getResources().getLimits();
                if (lim.get("cpu") != null) vo.setCpuLimit(lim.get("cpu").getAmount() + lim.get("cpu").getFormat());
                if (lim.get("memory") != null) vo.setMemLimit(lim.get("memory").getAmount() + lim.get("memory").getFormat());
            }
        }
        // Volume mounts
        if (container.getVolumeMounts() != null) {
            vo.setVolumeMounts(container.getVolumeMounts().stream().map(m -> {
                DeploymentDetailVO.VolumeMountVO vm = new DeploymentDetailVO.VolumeMountVO();
                vm.setName(m.getName());
                vm.setMountPath(m.getMountPath());
                vm.setReadOnly(m.getReadOnly() != null && m.getReadOnly() ? "true" : "false");
                vm.setSubPath(m.getSubPath());
                return vm;
            }).collect(Collectors.toList()));
        }
        // Ports
        if (container.getPorts() != null) {
            vo.setPorts(container.getPorts().stream().map(p -> {
                DeploymentDetailVO.PortVO pv = new DeploymentDetailVO.PortVO();
                pv.setName(p.getName());
                pv.setContainerPort(p.getContainerPort());
                pv.setProtocol(p.getProtocol());
                return pv;
            }).collect(Collectors.toList()));
        }
        if (container.getCommand() != null) vo.setCommand(String.join(" ", container.getCommand()));
        if (container.getArgs() != null) vo.setArgs(String.join(" ", container.getArgs()));
        return vo;
    }

    private ServiceSummaryVO toServiceSummary(io.fabric8.kubernetes.api.model.Service svc) {
        ServiceSummaryVO vo = new ServiceSummaryVO();
        ObjectMeta meta = svc.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        if (svc.getSpec() != null) {
            vo.setType(svc.getSpec().getType());
            vo.setClusterIP(svc.getSpec().getClusterIP());
            if (svc.getSpec().getExternalIPs() != null && !svc.getSpec().getExternalIPs().isEmpty()) {
                vo.setExternalIP(String.join(", ", svc.getSpec().getExternalIPs()));
            }
            if (svc.getSpec().getPorts() != null) {
                vo.setPortCount(svc.getSpec().getPorts().size());
            }
        }
        return vo;
    }

    private ServiceDetailVO toServiceDetail(io.fabric8.kubernetes.api.model.Service svc) {
        ServiceDetailVO vo = new ServiceDetailVO();
        ServiceSummaryVO summary = toServiceSummary(svc);
        vo.setName(summary.getName());
        vo.setNamespace(summary.getNamespace());
        vo.setType(summary.getType());
        vo.setClusterIP(summary.getClusterIP());
        vo.setExternalIP(summary.getExternalIP());
        vo.setPortCount(summary.getPortCount());
        vo.setAge(summary.getAge());
        vo.setCreationTimestamp(summary.getCreationTimestamp());

        vo.setUid(svc.getMetadata().getUid());
        vo.setLabels(svc.getMetadata().getLabels());
        vo.setAnnotations(svc.getMetadata().getAnnotations());
        if (svc.getSpec() != null) {
            vo.setSelector(svc.getSpec().getSelector());
            if (svc.getSpec().getSessionAffinity() != null) {
                vo.setSessionAffinity(svc.getSpec().getSessionAffinity());
            }
            // Port mappings
            if (svc.getSpec().getPorts() != null) {
                List<ServiceDetailVO.ServicePortVO> ports = svc.getSpec().getPorts().stream().map(p -> {
                    ServiceDetailVO.ServicePortVO sp = new ServiceDetailVO.ServicePortVO();
                    sp.setName(p.getName());
                    sp.setPort(p.getPort());
                    if (p.getTargetPort() != null) sp.setTargetPort(p.getTargetPort().getStrVal());
                    if (p.getNodePort() != null) sp.setNodePort(String.valueOf(p.getNodePort()));
                    sp.setProtocol(p.getProtocol());
                    return sp;
                }).collect(Collectors.toList());
                vo.setPorts(ports);
            }
        }
        return vo;
    }

    private EventVO toEventVO(io.fabric8.kubernetes.api.model.Event event) {
        EventVO vo = new EventVO();
        vo.setType(event.getType());
        vo.setReason(event.getReason());
        vo.setMessage(event.getMessage());
        vo.setCount(event.getCount());
        vo.setFirstTimestamp(toLocalDateTime(event.getFirstTimestamp()));
        vo.setLastTimestamp(toLocalDateTime(event.getLastTimestamp()));
        if (event.getInvolvedObject() != null) {
            vo.setInvolvedKind(event.getInvolvedObject().getKind());
            vo.setInvolvedName(event.getInvolvedObject().getName());
            vo.setInvolvedUid(event.getInvolvedObject().getUid());
        }
        vo.setSource(event.getSource() != null ? event.getSource().getComponent() : null);
        return vo;
    }

    // ==================== StatefulSet VO mapping ====================

    private StatefulSetSummaryVO toStatefulSetSummary(StatefulSet sts) {
        StatefulSetSummaryVO vo = new StatefulSetSummaryVO();
        ObjectMeta meta = sts.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        StatefulSetSpec spec = sts.getSpec();
        if (spec != null) {
            vo.setDesiredReplicas(spec.getReplicas() != null ? spec.getReplicas() : 0);
            if (spec.getServiceName() != null) {
                vo.setServiceName(spec.getServiceName());
            }
        }

        if (sts.getStatus() != null) {
            vo.setReadyReplicas(sts.getStatus().getReadyReplicas() != null ? sts.getStatus().getReadyReplicas() : 0);
            vo.setCurrentReplicas(sts.getStatus().getCurrentReplicas() != null ? sts.getStatus().getCurrentReplicas() : 0);
        }

        return vo;
    }

    // ==================== PVC VO mapping ====================

    private PvcSummaryVO toPvcSummary(PersistentVolumeClaim pvc) {
        PvcSummaryVO vo = new PvcSummaryVO();
        ObjectMeta meta = pvc.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        if (pvc.getStatus() != null) {
            vo.setStatus(pvc.getStatus().getPhase());
        }

        if (pvc.getSpec() != null) {
            if (pvc.getSpec().getAccessModes() != null) {
                vo.setAccessModes(String.join(", ", pvc.getSpec().getAccessModes()));
            }
            if (pvc.getSpec().getStorageClassName() != null) {
                vo.setStorageClass(pvc.getSpec().getStorageClassName());
            }
            if (pvc.getSpec().getResources() != null
                    && pvc.getSpec().getResources().getRequests() != null) {
                var requests = pvc.getSpec().getResources().getRequests();
                if (requests.containsKey("storage")) {
                    vo.setCapacity(requests.get("storage").getAmount()
                            + requests.get("storage").getFormat());
                }
            }
        }

        return vo;
    }

    // ==================== Secret VO mapping ====================

    private SecretSummaryVO toSecretSummary(Secret secret) {
        SecretSummaryVO vo = new SecretSummaryVO();
        ObjectMeta meta = secret.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        if (secret.getType() != null) {
            vo.setType(secret.getType());
        }

        if (secret.getData() != null) {
            vo.setDataCount(secret.getData().size());
        }

        return vo;
    }

    // ==================== ConfigMap VO mapping ====================

    private ConfigMapSummaryVO toConfigMapSummary(ConfigMap cm) {
        ConfigMapSummaryVO vo = new ConfigMapSummaryVO();
        ObjectMeta meta = cm.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));

        if (cm.getData() != null) {
            vo.setDataCount(cm.getData().size());
        }

        return vo;
    }

    // ==================== Node ====================

    public List<NodeSummaryVO> listNodes(Long clusterId, String keyword, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        List<Node> allNodes = client.nodes().list().getItems();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            allNodes = allNodes.stream()
                    .filter(n -> n.getMetadata().getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        // Get pod counts per node for summary
        Map<String, Long> nodePodCounts = client.pods().inAnyNamespace().list().getItems().stream()
                .filter(p -> p.getSpec() != null && p.getSpec().getNodeName() != null)
                .collect(Collectors.groupingBy(p -> p.getSpec().getNodeName(), Collectors.counting()));

        return allNodes.stream()
                .map(n -> toNodeSummary(n, nodePodCounts.getOrDefault(n.getMetadata().getName(), 0L).intValue()))
                .collect(Collectors.toList());
    }

    public NodeDetailVO getNode(Long clusterId, String name, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        Node node = client.nodes().withName(name).get();
        if (node == null) {
            throw new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND);
        }

        long podCount = client.pods().inAnyNamespace().list().getItems().stream()
                .filter(p -> p.getSpec() != null && name.equals(p.getSpec().getNodeName()))
                .count();

        return toNodeDetail(node, (int) podCount);
    }

    // ---- Node VO mapping ----

    private NodeSummaryVO toNodeSummary(Node node, int podCount) {
        NodeSummaryVO vo = new NodeSummaryVO();
        ObjectMeta meta = node.getMetadata();
        vo.setName(meta.getName());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));
        vo.setPodCount(podCount);

        // Status
        vo.setStatus(determineNodeStatus(node));

        // Role
        vo.setRole(determineNodeRole(node));

        // Node info
        if (node.getStatus() != null && node.getStatus().getNodeInfo() != null) {
            var info = node.getStatus().getNodeInfo();
            vo.setKubeletVersion(info.getKubeletVersion());
            vo.setContainerRuntime(info.getContainerRuntimeVersion());
            vo.setOsImage(info.getOsImage());
            vo.setKernelVersion(info.getKernelVersion());
            vo.setArchitecture(info.getArchitecture());
        }

        // Capacity
        if (node.getStatus() != null && node.getStatus().getCapacity() != null) {
            var capacity = node.getStatus().getCapacity();
            io.fabric8.kubernetes.api.model.Quantity cpu = capacity.get("cpu");
            if (cpu != null) {
                vo.setCpuCapacity((int) Math.round(cpu.getNumericalAmount().doubleValue()));
            }
            io.fabric8.kubernetes.api.model.Quantity mem = capacity.get("memory");
            if (mem != null) {
                vo.setMemoryCapacity(mem.getNumericalAmount().longValue() / (1024 * 1024));
            }
        }

        // Spec
        if (node.getSpec() != null) {
            vo.setPodCIDR(node.getSpec().getPodCIDR());
            vo.setProviderID(node.getSpec().getProviderID());
        }

        return vo;
    }

    private NodeDetailVO toNodeDetail(Node node, int podCount) {
        NodeDetailVO vo = new NodeDetailVO();
        NodeSummaryVO summary = toNodeSummary(node, podCount);
        vo.setName(summary.getName());
        vo.setStatus(summary.getStatus());
        vo.setRole(summary.getRole());
        vo.setKubeletVersion(summary.getKubeletVersion());
        vo.setContainerRuntime(summary.getContainerRuntime());
        vo.setOsImage(summary.getOsImage());
        vo.setKernelVersion(summary.getKernelVersion());
        vo.setArchitecture(summary.getArchitecture());
        vo.setPodCIDR(summary.getPodCIDR());
        vo.setProviderID(summary.getProviderID());
        vo.setPodCount(summary.getPodCount());
        vo.setCpuCapacity(summary.getCpuCapacity());
        vo.setMemoryCapacity(summary.getMemoryCapacity());
        vo.setAge(summary.getAge());

        ObjectMeta meta = node.getMetadata();
        vo.setUid(meta.getUid());
        vo.setLabels(meta.getLabels());
        vo.setAnnotations(meta.getAnnotations());
        if (meta.getCreationTimestamp() != null) {
            vo.setCreationTimestamp(meta.getCreationTimestamp());
        }

        // Addresses
        if (node.getStatus() != null && node.getStatus().getAddresses() != null) {
            vo.setAddresses(node.getStatus().getAddresses().stream().map(a -> {
                NodeDetailVO.NodeAddressVO addr = new NodeDetailVO.NodeAddressVO();
                addr.setType(a.getType());
                addr.setAddress(a.getAddress());
                return addr;
            }).collect(Collectors.toList()));
        }

        // Taints
        if (node.getSpec() != null && node.getSpec().getTaints() != null) {
            vo.setTaints(node.getSpec().getTaints().stream().map(t -> {
                NodeDetailVO.NodeTaintVO taint = new NodeDetailVO.NodeTaintVO();
                taint.setKey(t.getKey());
                taint.setValue(t.getValue());
                taint.setEffect(t.getEffect());
                return taint;
            }).collect(Collectors.toList()));
        }

        // Node info
        if (node.getStatus() != null && node.getStatus().getNodeInfo() != null) {
            var info = node.getStatus().getNodeInfo();
            NodeDetailVO.NodeSystemInfoVO sysInfo = new NodeDetailVO.NodeSystemInfoVO();
            sysInfo.setMachineID(info.getMachineID());
            sysInfo.setSystemUUID(info.getSystemUUID());
            sysInfo.setBootID(info.getBootID());
            sysInfo.setKernelVersion(info.getKernelVersion());
            sysInfo.setOsImage(info.getOsImage());
            sysInfo.setContainerRuntimeVersion(info.getContainerRuntimeVersion());
            sysInfo.setKubeletVersion(info.getKubeletVersion());
            sysInfo.setKubeProxyVersion(info.getKubeProxyVersion());
            sysInfo.setOperatingSystem(info.getOperatingSystem());
            sysInfo.setArchitecture(info.getArchitecture());
            vo.setNodeInfo(sysInfo);
        }

        // Capacity and allocatable
        if (node.getStatus() != null) {
            if (node.getStatus().getCapacity() != null) {
                Map<String, String> capMap = new LinkedHashMap<>();
                node.getStatus().getCapacity().forEach((k, v) ->
                        capMap.put(k, v.getAmount() + v.getFormat()));
                vo.setCapacity(capMap);
            }
            if (node.getStatus().getAllocatable() != null) {
                Map<String, String> allocMap = new LinkedHashMap<>();
                node.getStatus().getAllocatable().forEach((k, v) ->
                        allocMap.put(k, v.getAmount() + v.getFormat()));
                vo.setAllocatable(allocMap);
            }
            // Images
            if (node.getStatus().getImages() != null) {
                vo.setImages(node.getStatus().getImages().stream().map(img -> {
                    NodeDetailVO.NodeImageVO imgVo = new NodeDetailVO.NodeImageVO();
                    imgVo.setName(img.getNames() != null && !img.getNames().isEmpty() ? img.getNames().getFirst() : "");
                    imgVo.setSizeBytes(img.getSizeBytes() != null ? img.getSizeBytes() : 0);
                    return imgVo;
                }).collect(Collectors.toList()));
            }
        }

        return vo;
    }

    private static String determineNodeStatus(Node node) {
        if (node.getStatus() == null || node.getStatus().getConditions() == null) return "Unknown";
        for (var cond : node.getStatus().getConditions()) {
            if ("Ready".equals(cond.getType())) {
                return "True".equals(cond.getStatus()) ? "Ready" : "NotReady";
            }
        }
        return "Unknown";
    }

    private static String determineNodeRole(Node node) {
        if (node.getMetadata() == null || node.getMetadata().getLabels() == null) return "<none>";
        var labels = node.getMetadata().getLabels();
        if (labels.containsKey("node-role.kubernetes.io/control-plane")) return "control-plane";
        if (labels.containsKey("node-role.kubernetes.io/master")) return "master";
        // Try to find any node-role label
        for (String key : labels.keySet()) {
            if (key.startsWith("node-role.kubernetes.io/")) {
                return key.substring("node-role.kubernetes.io/".length());
            }
        }
        return "<none>";
    }

    // ==================== StorageClass ====================

    public List<StorageClassSummaryVO> listStorageClasses(Long clusterId, Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        return client.resources(StorageClass.class).list().getItems().stream()
                .map(this::toStorageClassSummary)
                .sorted((a, b) -> {
                    if (a.getCreationTimestamp() == null) return 1;
                    if (b.getCreationTimestamp() == null) return -1;
                    return b.getCreationTimestamp().compareTo(a.getCreationTimestamp());
                })
                .collect(Collectors.toList());
    }

    private StorageClassSummaryVO toStorageClassSummary(StorageClass sc) {
        StorageClassSummaryVO vo = new StorageClassSummaryVO();
        ObjectMeta meta = sc.getMetadata();
        vo.setName(meta.getName());
        vo.setCreationTimestamp(toLocalDateTime(meta.getCreationTimestamp()));
        vo.setAge(formatAge(meta.getCreationTimestamp()));
        vo.setProvisioner(sc.getProvisioner());

        if (sc.getReclaimPolicy() != null) {
            vo.setReclaimPolicy(sc.getReclaimPolicy());
        }
        if (sc.getVolumeBindingMode() != null) {
            vo.setVolumeBindingMode(sc.getVolumeBindingMode());
        }
        vo.setAllowVolumeExpansion(sc.getAllowVolumeExpansion());
        return vo;
    }

    // ==================== Helpers ====================

    private static void requireNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new BizException(ContainerErrorCode.RESOURCE_NAMESPACE_REQUIRED);
        }
    }

    private static LocalDateTime toLocalDateTime(String timestamp) {
        if (timestamp == null) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatAge(String timestamp) {
        if (timestamp == null) return "";
        try {
            Instant then = Instant.parse(timestamp);
            Duration d = Duration.between(then, Instant.now());
            long days = d.toDays();
            long hours = d.toHours() % 24;
            long minutes = d.toMinutes() % 60;
            if (days > 0) return days + "d" + (hours > 0 ? hours + "h" : "");
            if (hours > 0) return hours + "h" + minutes + "m";
            if (minutes > 0) return minutes + "m";
            return d.toSeconds() + "s";
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> parseLabels(String labels) {
        Map<String, String> result = new HashMap<>();
        if (labels == null || labels.isBlank()) return result;
        for (String pair : labels.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }
}