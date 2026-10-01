package com.hfwas.devops.container.service.argo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.config.argo.ArgoWorkflowProperties;
import com.hfwas.devops.container.dto.argo.*;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Argo Workflows CRD 操作服务。
 * 通过 Fabric8 GenericKubernetesResource 直连 argoproj.io/v1alpha1 CRD，
 * 不走 Argo Server HTTP API。
 */
@Slf4j
@Service
public class ArgoWorkflowService {

    private static final String API_GROUP = "argoproj.io";
    private static final String API_VERSION = "v1alpha1";
    private static final String KIND_WORKFLOW = "workflows";
    private static final String KIND_TEMPLATE = "workflowtemplates";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ResourceDefinitionContext workflowCtx;
    private final ResourceDefinitionContext templateCtx;

    private final KubernetesClient kubernetesClient;
    private final ArgoWorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final boolean available;

    public ArgoWorkflowService(
            ObjectProvider<KubernetesClient> kubernetesClientProvider,
            ArgoWorkflowProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        // 优先用 argo.kubeconfig 创建专属客户端，兼容 Docker Desktop 等非 pipeline 集群
        KubernetesClient ownClient = tryCreateOwnClient(properties);
        if (ownClient != null) {
            this.kubernetesClient = ownClient;
            this.available = true;
            log.info("ArgoWorkflowService: 使用 argo.kubeconfig={}", properties.getKubeconfig());
        } else {
            // 回退到 pipeline 的 KubernetesClient bean
            this.kubernetesClient = kubernetesClientProvider.getIfAvailable();
            this.available = this.kubernetesClient != null;
            if (this.available) {
                log.info("ArgoWorkflowService: 复用 pipeline KubernetesClient bean");
            } else {
                log.warn("ArgoWorkflowService: KubernetesClient 不可用。" +
                        "请配置 argo.kubeconfig 或 pipeline.kubeconfig");
            }
        }

        this.workflowCtx = buildContext(API_GROUP, API_VERSION, KIND_WORKFLOW);
        this.templateCtx = buildContext(API_GROUP, API_VERSION, KIND_TEMPLATE);
    }

    /**
     * 尝试从 argo.kubeconfig 路径创建专属 KubernetesClient。
     * 路径不存在或文件不可读时返回 null。
     */
    private static KubernetesClient tryCreateOwnClient(ArgoWorkflowProperties props) {
        String kubeconfig = props.getKubeconfig();
        if (!StringUtils.hasText(kubeconfig)) {
            return null;
        }
        Path path = Path.of(kubeconfig);
        if (!Files.isRegularFile(path)) {
            log.warn("argo.kubeconfig 文件不存在: {}", kubeconfig);
            return null;
        }
        try {
            Config config = Config.fromKubeconfig(Files.readString(path));
            log.info("KubernetesClient created from argo.kubeconfig: {}", kubeconfig);
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (IOException e) {
            log.warn("读取 argo.kubeconfig 失败: {} - {}", kubeconfig, e.getMessage());
            return null;
        }
    }

    private static ResourceDefinitionContext buildContext(String group, String version, String plural) {
        return new ResourceDefinitionContext.Builder()
                .withGroup(group)
                .withVersion(version)
                .withPlural(plural)
                .build();
    }

    // ==================== Workflows（运行实例）========================

    public IPage<WorkflowSummaryVO> pageWorkflows(int pageNo, int pageSize, String keyword, String status) {
        requireAvailable();
        List<GenericKubernetesResource> all = listWorkflows();
        Stream<GenericKubernetesResource> stream = all.stream();

        // keyword filter (name)
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            stream = stream.filter(w -> w.getMetadata().getName().toLowerCase().contains(lower));
        }
        // status filter
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            stream = stream.filter(w -> status.equalsIgnoreCase(getPhase(w)));
        }

        // Sort by creation timestamp desc
        List<GenericKubernetesResource> filtered = stream
                .sorted((a, b) -> {
                    String t1 = a.getMetadata().getCreationTimestamp();
                    String t2 = b.getMetadata().getCreationTimestamp();
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<WorkflowSummaryVO> records = from < total
                ? filtered.subList(from, to).stream().map(this::toWorkflowSummary).collect(Collectors.toList())
                : List.of();

        Page<WorkflowSummaryVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public WorkflowDetailVO getWorkflow(String name) {
        requireAvailable();
        GenericKubernetesResource wf = workflowOp().withName(name).get();
        if (wf == null) {
            throw new BizException(30301, "工作流不存在: " + name);
        }
        return toWorkflowDetail(wf);
    }

    public void createWorkflow(JsonNode spec) {
        requireAvailable();
        GenericKubernetesResource resource = buildCrd(spec);
        workflowOp().resource(resource).create();
        log.info("Workflow created: {}", resource.getMetadata().getName());
    }

    public void deleteWorkflow(String name) {
        requireAvailable();
        boolean deleted = workflowOp().withName(name).delete().size() > 0;
        if (!deleted) {
            throw new BizException(30302, "删除工作流失败（可能不存在）: " + name);
        }
        log.info("Workflow deleted: {}", name);
    }

    /**
     * 从 WorkflowTemplate 提交运行。
     * 创建 Workflow CR，spec.workflowTemplateRef 引用模板。
     */
    public String submitWorkflow(String templateName) {
        requireAvailable();
        // 先查模板是否存在
        GenericKubernetesResource tmpl = templateOp().withName(templateName).get();
        if (tmpl == null) {
            throw new BizException(30303, "WorkflowTemplate 不存在: " + templateName);
        }

        ObjectNode wfSpec = objectMapper.createObjectNode();
        wfSpec.put("entrypoint", tmpl.get("spec", "entrypoint") != null
                ? tmpl.get("spec", "entrypoint").toString() : "");
        // workflowTemplateRef
        ObjectNode ref = objectMapper.createObjectNode();
        ref.put("name", templateName);
        wfSpec.set("workflowTemplateRef", ref);
        // 继承模板参数
        JsonNode tmplArgs = tmpl.get("spec", "arguments");
        if (tmplArgs != null) {
            wfSpec.set("arguments", tmplArgs.deepCopy());
        }

        // Build Workflow CR
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("generateName", templateName + "-");
        metadata.put("namespace", properties.getNamespace());
        // 加标签关联模板
        ObjectNode labels = objectMapper.createObjectNode();
        labels.put(properties.getTemplateLabelKey(), templateName);
        metadata.set("labels", labels);
        body.set("metadata", metadata);
        body.set("spec", wfSpec);

        GenericKubernetesResource resource = buildCrd(body);
        GenericKubernetesResource created = workflowOp().resource(resource).create();
        String name = created.getMetadata().getName();
        log.info("Workflow submitted from template {}: {}", templateName, name);
        return name;
    }

    // ==================== Runs ====================

    public IPage<WorkflowRunVO> pageRuns(String templateName, int pageNo, int pageSize) {
        requireAvailable();
        List<GenericKubernetesResource> all = listWorkflows();

        // Filter by template label
        Stream<GenericKubernetesResource> stream = all.stream();
        if (templateName != null && !templateName.isBlank()) {
            String labelKey = properties.getTemplateLabelKey();
            stream = stream.filter(w -> {
                Map<String, String> labels = w.getMetadata().getLabels();
                return labels != null && templateName.equals(labels.get(labelKey));
            });
        }

        List<GenericKubernetesResource> filtered = stream
                .sorted((a, b) -> {
                    String t1 = a.getMetadata().getCreationTimestamp();
                    String t2 = b.getMetadata().getCreationTimestamp();
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<WorkflowRunVO> records = from < total
                ? filtered.subList(from, to).stream().map(this::toWorkflowRun).collect(Collectors.toList())
                : List.of();

        Page<WorkflowRunVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public WorkflowRunVO getRun(String runId) {
        requireAvailable();
        GenericKubernetesResource wf = workflowOp().withName(runId).get();
        if (wf == null) {
            throw new BizException(30304, "工作流运行不存在: " + runId);
        }
        return toWorkflowRun(wf);
    }

    public List<WorkflowRunLogVO> getRunLogs(String runId, String nodeId) {
        requireAvailable();
        GenericKubernetesResource wf = workflowOp().withName(runId).get();
        if (wf == null) {
            throw new BizException(30304, "工作流运行不存在: " + runId);
        }

        // 获取对应 node 的 podName
        JsonNode nodes = wf.get("status", "nodes");
        String podName = null;
        if (nodes != null && nodes.isObject() && nodeId != null) {
            JsonNode node = nodes.get(nodeId);
            if (node != null) {
                podName = node.path("podName").asText(null);
            }
        }
        if (podName == null) {
            return List.of();
        }

        // 从 K8s API 读取 Pod 日志
        try {
            String logs = kubernetesClient.pods().inNamespace(properties.getNamespace())
                    .withName(podName)
                    .getLog();

            String nodeName = nodeId != null && nodes != null && nodes.isObject()
                    ? nodes.path(nodeId).path("displayName").asText(nodeId)
                    : nodeId;

            String phase = nodeId != null && nodes != null && nodes.isObject()
                    ? nodes.path(nodeId).path("phase").asText("")
                    : "";

            WorkflowRunLogVO vo = new WorkflowRunLogVO();
            vo.setNodeId(nodeId);
            vo.setNodeName(nodeName);
            vo.setLogs(logs);
            vo.setPhase(phase);
            return List.of(vo);
        } catch (Exception e) {
            log.warn("Failed to get logs for pod {}: {}", podName, e.getMessage());
            return List.of();
        }
    }

    public List<WorkflowEventVO> getRunEvents(String runId) {
        requireAvailable();
        GenericKubernetesResource wf = workflowOp().withName(runId).get();
        if (wf == null) {
            throw new BizException(30304, "工作流运行不存在: " + runId);
        }

        // 获取 Workflow 关联的 Pod 列表
        String uid = wf.getMetadata().getUid();
        try {
            var pods = kubernetesClient.pods().inNamespace(properties.getNamespace()).list().getItems();
            List<GenericKubernetesResource> allEvents = kubernetesClient
                    .genericKubernetesResources(buildContext("", "v1", "events"))
                    .inNamespace(properties.getNamespace())
                    .list()
                    .getItems();

            // Filter events related to this workflow's pods or the workflow itself
            Set<String> podNames = pods.stream()
                    .filter(p -> {
                        OwnerReference owner = p.getMetadata().getOwnerReferences().stream()
                                .filter(o -> uid != null && uid.equals(o.getUid()))
                                .findFirst().orElse(null);
                        return owner != null;
                    })
                    .map(p -> p.getMetadata().getName())
                    .collect(Collectors.toSet());
            podNames.add(runId);

            return allEvents.stream()
                    .filter(e -> {
                        Object involved = e.get("involvedObject", "name");
                        return involved != null && podNames.contains(involved.toString());
                    })
                    .map(this::toWorkflowEvent)
                    .sorted((a, b) -> {
                        if (a.getTimestamp() == null) return 1;
                        if (b.getTimestamp() == null) return -1;
                        return a.getTimestamp().compareTo(b.getTimestamp());
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get events for workflow {}: {}", runId, e.getMessage());
            return List.of();
        }
    }

    // ==================== Templates ====================

    public IPage<WorkflowTemplateVO> pageTemplates(int pageNo, int pageSize, String keyword) {
        requireAvailable();
        List<GenericKubernetesResource> all = templateOp().list().getItems();
        Stream<GenericKubernetesResource> stream = all.stream();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            stream = stream.filter(t -> t.getMetadata().getName().toLowerCase().contains(lower));
        }

        List<GenericKubernetesResource> filtered = stream
                .sorted((a, b) -> {
                    String t1 = a.getMetadata().getCreationTimestamp();
                    String t2 = b.getMetadata().getCreationTimestamp();
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<WorkflowTemplateVO> records = from < total
                ? filtered.subList(from, to).stream().map(this::toWorkflowTemplate).collect(Collectors.toList())
                : List.of();

        Page<WorkflowTemplateVO> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public WorkflowTemplateVO getTemplate(String name) {
        requireAvailable();
        GenericKubernetesResource tmpl = templateOp().withName(name).get();
        if (tmpl == null) {
            throw new BizException(30305, "WorkflowTemplate 不存在: " + name);
        }
        return toWorkflowTemplate(tmpl);
    }

    public void saveTemplate(JsonNode spec) {
        requireAvailable();
        String name = spec.has("metadata") && spec.get("metadata").has("name")
                ? spec.get("metadata").get("name").asText() : null;

        GenericKubernetesResource resource = buildCrd(spec);

        if (name != null && templateOp().withName(name).get() != null) {
            // Update existing
            templateOp().withName(name).replace(resource);
            log.info("WorkflowTemplate updated: {}", name);
        } else {
            // Create new
            GenericKubernetesResource created = templateOp().resource(resource).create();
            log.info("WorkflowTemplate created: {}", created.getMetadata().getName());
        }
    }

    // ==================== Private helpers ====================

    private void requireAvailable() {
        if (!available) {
            throw new BizException(30300, "KubernetesClient 不可用，请配置 pipeline.kubeconfig");
        }
    }

    private GenericKubernetesResource buildCrd(JsonNode spec) {
        try {
            String json = objectMapper.writeValueAsString(spec);
            HasMetadata result = kubernetesClient.resource(json).get();
            if (!(result instanceof GenericKubernetesResource gkr)) {
                throw new BizException(30306, "无法解析为 K8s 自定义资源");
            }
            return gkr;
        } catch (JsonProcessingException e) {
            throw new BizException(30306, "无效的工作流定义: " + e.getMessage());
        }
    }

    private List<GenericKubernetesResource> listWorkflows() {
        return workflowOp().list().getItems();
    }

    private NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> workflowOp() {
        return kubernetesClient
                .genericKubernetesResources(workflowCtx)
                .inNamespace(properties.getNamespace());
    }

    private NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> templateOp() {
        return kubernetesClient
                .genericKubernetesResources(templateCtx)
                .inNamespace(properties.getNamespace());
    }

    // ---- CRD → VO mapping ----

    private WorkflowSummaryVO toWorkflowSummary(GenericKubernetesResource wf) {
        WorkflowSummaryVO vo = new WorkflowSummaryVO();
        ObjectMeta meta = wf.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setStatus(getPhase(wf));
        vo.setStartedAt(getTimeStr(wf, "status", "startedAt"));
        vo.setFinishedAt(getTimeStr(wf, "status", "finishedAt"));
        vo.setDuration(calculateDuration(vo.getStartedAt(), vo.getFinishedAt()));
        vo.setEntrypoint(getString(wf, "spec", "entrypoint"));
        vo.setTrigger(detectTrigger(meta));
        return vo;
    }

    private WorkflowDetailVO toWorkflowDetail(GenericKubernetesResource wf) {
        WorkflowDetailVO vo = new WorkflowDetailVO();
        ObjectMeta meta = wf.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        String phase = getPhase(wf);
        vo.setStatus(phase);
        vo.setPhase(phase);
        vo.setStartedAt(getTimeStr(wf, "status", "startedAt"));
        vo.setFinishedAt(getTimeStr(wf, "status", "finishedAt"));
        vo.setDuration(calculateDuration(vo.getStartedAt(), vo.getFinishedAt()));
        vo.setEntrypoint(getString(wf, "spec", "entrypoint"));
        vo.setTrigger(detectTrigger(meta));

        // parameters
        JsonNode args = wf.get("spec", "arguments", "parameters");
        if (args != null && args.isArray()) {
            List<Map<String, Object>> params = new ArrayList<>();
            for (JsonNode p : args) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", p.path("name").asText());
                map.put("value", p.path("value").asText(null));
                params.add(map);
            }
            vo.setParameters(params);
        }

        // templates
        JsonNode templates = wf.get("spec", "templates");
        if (templates != null && templates.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            templates.forEach(list::add);
            vo.setTemplates(list);
        }

        // nodeStatus
        JsonNode nodes = wf.get("status", "nodes");
        if (nodes != null && nodes.isObject()) {
            Map<String, JsonNode> map = new LinkedHashMap<>();
            nodes.fieldNames().forEachRemaining(key -> map.put(key, nodes.get(key)));
            vo.setNodeStatus(map);
        }

        return vo;
    }

    private WorkflowRunVO toWorkflowRun(GenericKubernetesResource wf) {
        WorkflowRunVO vo = new WorkflowRunVO();
        ObjectMeta meta = wf.getMetadata();
        vo.setId(meta.getName());
        // Try to get template name from label
        Map<String, String> labels = meta.getLabels();
        if (labels != null && labels.containsKey(properties.getTemplateLabelKey())) {
            vo.setWorkflowName(labels.get(properties.getTemplateLabelKey()));
        }
        vo.setStatus(getPhase(wf));
        vo.setStartedAt(getTimeStr(wf, "status", "startedAt"));
        vo.setFinishedAt(getTimeStr(wf, "status", "finishedAt"));
        vo.setDuration(calculateDuration(vo.getStartedAt(), vo.getFinishedAt()));
        vo.setTrigger(detectTrigger(meta));

        // nodeStatus
        JsonNode nodes = wf.get("status", "nodes");
        if (nodes != null && nodes.isObject()) {
            Map<String, JsonNode> map = new LinkedHashMap<>();
            nodes.fieldNames().forEachRemaining(key -> map.put(key, nodes.get(key)));
            vo.setNodeStatus(map);
        }

        return vo;
    }

    private WorkflowTemplateVO toWorkflowTemplate(GenericKubernetesResource tmpl) {
        WorkflowTemplateVO vo = new WorkflowTemplateVO();
        ObjectMeta meta = tmpl.getMetadata();
        vo.setName(meta.getName());
        vo.setNamespace(meta.getNamespace());
        vo.setEntrypoint(getString(tmpl, "spec", "entrypoint"));
        vo.setCreatedAt(meta.getCreationTimestamp());

        // description from annotations
        Map<String, String> annotations = meta.getAnnotations();
        if (annotations != null) {
            vo.setDescription(annotations.getOrDefault("description", ""));
        }

        // templates
        JsonNode templates = tmpl.get("spec", "templates");
        if (templates != null && templates.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            templates.forEach(list::add);
            vo.setTemplates(list);
        }

        // arguments
        JsonNode args = tmpl.get("spec", "arguments", "parameters");
        if (args != null && args.isArray()) {
            List<Map<String, Object>> params = new ArrayList<>();
            for (JsonNode p : args) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", p.path("name").asText());
                map.put("value", p.path("value").asText(null));
                map.put("description", p.path("description").asText(null));
                params.add(map);
            }
            vo.setArguments(params);
        }

        return vo;
    }

    private WorkflowEventVO toWorkflowEvent(GenericKubernetesResource event) {
        WorkflowEventVO vo = new WorkflowEventVO();
        vo.setId(event.getMetadata().getName());
        vo.setType(getString(event, "type"));
        vo.setReason(getString(event, "reason"));
        vo.setMessage(getString(event, "message"));
        vo.setTimestamp(getString(event, "lastTimestamp"));
        vo.setSource(getString(event, "source", "component"));
        return vo;
    }

    // ---- Field extraction helpers ----

    private static String getPhase(GenericKubernetesResource wf) {
        JsonNode phase = wf.get("status", "phase");
        return phase != null ? phase.asText("Pending") : "Pending";
    }

    private static String getTimeStr(GenericKubernetesResource wf, String... path) {
        JsonNode node = wf.get(path);
        return node != null ? node.asText() : null;
    }

    private static String getString(GenericKubernetesResource r, String... path) {
        JsonNode node = r.get(path);
        return node != null ? node.asText() : null;
    }

    private static String calculateDuration(String startedAt, String finishedAt) {
        if (startedAt == null) return null;
        try {
            OffsetDateTime start = OffsetDateTime.parse(startedAt, TIME_FMT);
            OffsetDateTime end = finishedAt != null
                    ? OffsetDateTime.parse(finishedAt, TIME_FMT)
                    : OffsetDateTime.now();
            Duration d = Duration.between(start, end);
            long secs = d.getSeconds();
            if (secs < 60) return secs + "s";
            if (secs < 3600) return (secs / 60) + "m" + (secs % 60) + "s";
            return (secs / 3600) + "h" + ((secs % 3600) / 60) + "m";
        } catch (Exception e) {
            return null;
        }
    }

    private static String detectTrigger(ObjectMeta meta) {
        if (meta == null) return "manual";
        Map<String, String> labels = meta.getLabels();
        if (labels != null) {
            if (labels.containsKey("workflows.argoproj.io/cron-schedule")) return "cron";
            if (labels.containsKey("workflows.argoproj.io/event")) return "event";
        }
        return "manual";
    }
}