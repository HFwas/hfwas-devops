package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelinePageQuery;
import com.hfwas.devops.pipeline.dto.PipelineRunJobVO;
import com.hfwas.devops.pipeline.dto.PipelineRunStartDTO;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.dto.RunParamDefinitionVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobParamEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
import com.hfwas.devops.pipeline.dto.JobParamBindingDTO;
import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.graph.ApprovalPlan;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineJobParamMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.hfwas.devops.pipeline.param.ParamBindings;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineRunService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(PipelineRunService.class);

    private final PipelineDefinitionService definitionService;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final PipelineJobParamMapper jobParamMapper;
    private final PipelineTaskKindParamMapper taskKindParamMapper;
    private final CurrentUserAccessor currentUserAccessor;
    private final PipelineExecutor pipelineExecutor;
    private final int maxConcurrentRuns;

    public PipelineRunService(
            PipelineDefinitionService definitionService,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            PipelineJobParamMapper jobParamMapper,
            PipelineTaskKindParamMapper taskKindParamMapper,
            CurrentUserAccessor currentUserAccessor,
            @Lazy PipelineExecutor pipelineExecutor,
            @Value("${pipeline.max-concurrent-runs:10}") int maxConcurrentRuns
    ) {
        this.definitionService = definitionService;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.jobParamMapper = jobParamMapper;
        this.taskKindParamMapper = taskKindParamMapper;
        this.currentUserAccessor = currentUserAccessor;
        this.pipelineExecutor = pipelineExecutor;
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    // ---- 运行时参数查询 ----

    /**
     * 获取流水线所有可调参数的默认值 + 选项，供前端弹框渲染。
     * 对 api_select 类型，会预调用远程 API 获取选项列表。
     */
    public List<RunParamDefinitionVO> getDefaultParams(Long pipelineId) {
        PipelineEntity pipeline = definitionService.requireOwned(pipelineId);
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));
        Map<String, List<PipelineTaskKindParamEntity>> byKind = taskKindParamMapper.selectAll().stream()
                .collect(Collectors.groupingBy(PipelineTaskKindParamEntity::getKindValue));
        List<RunParamDefinitionVO> result = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (PipelineJobEntity job : jobs) {
            Map<String, JobParamBindingDTO> bindings = ParamBindings.parse(job.getParamBindings());
            List<PipelineTaskKindParamEntity> defs = byKind.getOrDefault(job.getKind(), List.of());
            for (PipelineTaskKindParamEntity def : defs) {
                JobParamBindingDTO binding = bindings.get(def.getParamKey());
                if (!ParamBindings.isRuntime(binding)) {
                    continue;
                }
                if (!seen.add(def.getParamKey())) {
                    continue;
                }
                RunParamDefinitionVO vo = toRunParamVo(def);
                if ("GIT_REF".equals(def.getParamKey()) && pipeline.getGitRef() != null && !pipeline.getGitRef().isBlank()) {
                    vo.setDefaultValue(pipeline.getGitRef());
                }
                if ("api_select".equals(def.getParamType())
                        && def.getApiUrl() != null && !def.getApiUrl().isBlank()) {
                    try {
                        List<String> options = fetchApiOptions(
                                def.getApiUrl(),
                                def.getApiMethod(),
                                parseJsonMap(def.getApiHeadersJson()),
                                def.getApiResponsePath()
                        );
                        vo.setOptions(options);
                    } catch (Exception e) {
                        log.warn("pre-fetch api options failed for param {}: {}", def.getParamKey(), e.getMessage());
                    }
                }
                vo.setLoading(false);
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 调用远程 API 获取选项列表。
     */
    private static List<String> fetchApiOptions(String apiUrl, String apiMethod,
                                                  Map<String, String> apiHeaders,
                                                  String apiResponsePath) {
        if (apiUrl == null || apiUrl.isBlank()) return List.of();
        try {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .timeout(java.time.Duration.ofSeconds(10));
            if ("POST".equalsIgnoreCase(apiMethod)) {
                builder = builder.method("POST", java.net.http.HttpRequest.BodyPublishers.noBody());
            } else {
                builder = builder.GET();
            }
            if (apiHeaders != null) {
                for (Map.Entry<String, String> entry : apiHeaders.entrySet()) {
                    builder = builder.header(entry.getKey(), entry.getValue());
                }
            }
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(
                    builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            return parseApiResponse(response.body(), apiResponsePath);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseApiResponse(String body, String jsonPath) {
        if (body == null || body.isBlank()) return List.of();
        try {
            if (jsonPath == null || jsonPath.isBlank()) {
                return JSON.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            }
            // 简易 JSONPath: $[].name 或 $.data[].name
            String path = jsonPath.trim();
            if (path.startsWith("$")) path = path.substring(1);
            if (path.startsWith(".")) path = path.substring(1);
            Object root = JSON.readValue(body, Object.class);
            Object current = root;
            for (String seg : path.split("\\.")) {
                if (seg.isEmpty()) continue;
                if (seg.contains("[]")) {
                    String key = seg.replace("[]", "");
                    if (current instanceof Map) current = ((Map<String, Object>) current).get(key);
                    if (current instanceof List) {
                        return ((List<Object>) current).stream()
                                .map(Object::toString)
                                .collect(Collectors.toList());
                    }
                } else {
                    if (current instanceof Map) current = ((Map<String, Object>) current).get(seg);
                }
            }
            if (current instanceof List) {
                return ((List<Object>) current).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static RunParamDefinitionVO toRunParamVo(PipelineTaskKindParamEntity entity) {
        RunParamDefinitionVO vo = new RunParamDefinitionVO();
        vo.setParamKey(entity.getParamKey());
        vo.setParamLabel(entity.getParamLabel());
        vo.setParamType(entity.getParamType());
        vo.setDefaultValue(entity.getDefaultValue());
        vo.setRequired(entity.getRequired() != null && entity.getRequired() == 1);
        vo.setPlaceholder(entity.getPlaceholder());
        if ("select".equals(entity.getParamType())) {
            vo.setOptions(parseJsonArray(entity.getOptionsJson()));
        } else if ("api_select".equals(entity.getParamType())) {
            vo.setLoading(true);
        }
        return vo;
    }

    private static RunParamDefinitionVO toRunParamVo(PipelineJobParamEntity entity) {
        RunParamDefinitionVO vo = new RunParamDefinitionVO();
        vo.setParamKey(entity.getParamKey());
        vo.setParamLabel(entity.getParamLabel());
        vo.setParamType(entity.getParamType());
        vo.setDefaultValue(entity.getDefaultValue());
        vo.setRequired(entity.getRequired() != null && entity.getRequired() == 1);
        vo.setPlaceholder(entity.getPlaceholder());
        if ("select".equals(entity.getParamType())) {
            vo.setOptions(parseJsonArray(entity.getOptionsJson()));
        } else if ("api_select".equals(entity.getParamType())) {
            vo.setLoading(true);
        }
        return vo;
    }

    // ---- 启动运行（带运行时参数） ----

    @Transactional
    public PipelineRunVO start(Long pipelineId, Map<String, String> runtimeParams) {
        return doStart(pipelineId, runtimeParams);
    }

    /**
     * 向后兼容：无参数启动。
     */
    @Transactional
    public PipelineRunVO start(Long pipelineId) {
        return doStart(pipelineId, null);
    }

    @Transactional
    public PipelineRunVO start(Long pipelineId, PipelineRunStartDTO dto) {
        return doStart(pipelineId, dto != null ? dto.getParams() : null);
    }

    private PipelineRunVO doStart(Long pipelineId, Map<String, String> runtimeParams) {
        PipelineEntity pipeline = definitionService.requireOwned(pipelineId);
        List<PipelineStageEntity> stages = stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                .eq(PipelineStageEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineStageEntity::getSortOrder));
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));

        Map<String, String> effectiveParams = mergeRuntimeParams(pipelineId, runtimeParams);
        validateRuntimeParams(pipelineId, effectiveParams);

        boolean clusterReady = pipelineExecutor.isReady();
        PipelineGraphSpec graph = definitionService.loadGraph(pipelineId);
        ApprovalPlan plan = ApprovalPlan.of(graph);
        boolean waitFirst = clusterReady && plan.waitBeforeFirst();
        String status = !clusterReady ? "FAILED" : (waitFirst ? "WAITING_APPROVAL" : "QUEUED");
        String error = clusterReady ? null : "未配置执行集群（pipeline.kubeconfig），定义已保存，暂不能真正执行";

        // 合并运行时参数 — GIT_REF 特殊处理
        String effectiveGitRef = effectiveParams.containsKey("GIT_REF")
                ? effectiveParams.get("GIT_REF")
                : pipeline.getGitRef();

        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(pipelineId);
        run.setTenantId(pipeline.getTenantId());
        run.setStatus(status);
        run.setTrigger("MANUAL");
        run.setGitRef(effectiveGitRef);
        run.setTriggeredByName(currentUserAccessor.currentDisplayName());
        run.setErrorMessage(error);
        run.setStartedAt(LocalDateTime.now());
        run.setRuntimeParams(toJson(effectiveParams));
        if (!clusterReady) {
            run.setFinishedAt(LocalDateTime.now());
        }
        run.setCreateBy(currentUserAccessor.currentUserId());
        runMapper.insert(run);

        for (PipelineStageEntity stage : stages) {
            List<PipelineJobEntity> stageJobs = jobs.stream()
                    .filter(item -> stage.getId().equals(item.getStageId()))
                    .sorted(java.util.Comparator.comparingInt(PipelineJobEntity::getSortOrder))
                    .toList();
            for (PipelineJobEntity job : stageJobs) {
                String command = buildCommand(job, effectiveParams);
                PipelineRunJobEntity runJob = new PipelineRunJobEntity();
                runJob.setRunId(run.getId());
                runJob.setJobId(job.getId());
                runJob.setStageName(stage.getName());
                runJob.setJobName(job.getName());
                runJob.setKind(job.getKind());
                runJob.setCommand(command);
                runJob.setStatus(clusterReady ? (waitFirst && isSameApproval(plan.firstApproval(), job)
                        ? "WAITING_APPROVAL" : "QUEUED") : "FAILED");
                runJob.setLogText(clusterReady ? null : error);
                runJobMapper.insert(runJob);
            }
        }
        if (clusterReady && !waitFirst && !plan.segments().isEmpty()) {
            run.setSegmentIndex(0);
            runMapper.updateById(run);
            if (canSubmitNow(run.getTenantId())) {
                submitAfterCommit(run.getId());
            }
            // 超出最大并发数，保持 QUEUED 状态等待调度
        }
        return get(pipelineId, run.getId());
    }

    public PipelineRunVO get(Long pipelineId, Long runId) {
        definitionService.requireOwned(pipelineId);
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null || !pipelineId.equals(run.getPipelineId())) {
            throw BizException.of(ResultCode.NOT_FOUND, "运行记录不存在");
        }
        return toVo(pipelineId, run, true);
    }

    public IPage<PipelineRunVO> pageRuns(Long pipelineId, PipelinePageQuery query) {
        definitionService.requireOwned(pipelineId);
        Page<PipelineRunEntity> page = new Page<>(query.resolvePageNo(), query.resolvePageSize());
        IPage<PipelineRunEntity> rows = runMapper.selectPage(page, new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, pipelineId)
                .orderByDesc(PipelineRunEntity::getId));
        return rows.convert(run -> toVo(pipelineId, run, false));
    }

    @Transactional
    public PipelineRunVO cancel(Long pipelineId, Long runId) {
        PipelineRunVO current = get(pipelineId, runId);
        if ("SUCCEEDED".equals(current.getStatus()) || "FAILED".equals(current.getStatus())
                || "CANCELLED".equals(current.getStatus())) {
            return current;
        }
        PipelineRunEntity run = runMapper.selectById(runId);
        run.setStatus("CANCELLED");
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        try {
            pipelineExecutor.cancel(runId);
        } catch (Exception ignored) {
            // cluster cancel is best-effort
        }
        return get(pipelineId, runId);
    }

    @Transactional
    public PipelineRunVO approve(Long pipelineId, Long runId) {
        PipelineRunVO current = get(pipelineId, runId);
        if (!"WAITING_APPROVAL".equals(current.getStatus())) {
            throw BizException.of(ResultCode.BAD_REQUEST, "当前运行不在待审批");
        }
        ApprovalPlan plan = ApprovalPlan.of(definitionService.loadGraph(pipelineId));
        PipelineRunEntity run = runMapper.selectById(runId);
        List<PipelineRunJobEntity> runJobs = runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                .eq(PipelineRunJobEntity::getRunId, runId)
                .orderByAsc(PipelineRunJobEntity::getId));
        runJobs.stream()
                .filter(job -> "WAITING_APPROVAL".equals(job.getStatus())
                        && PipelineJobKind.APPROVAL.name().equals(job.getKind()))
                .findFirst()
                .ifPresent(job -> {
                    job.setStatus("SUCCEEDED");
                    job.setFinishedAt(LocalDateTime.now());
                    runJobMapper.updateById(job);
                });
        int succeeded = (int) runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                        .eq(PipelineRunJobEntity::getRunId, runId))
                .stream()
                .filter(job -> PipelineJobKind.APPROVAL.name().equals(job.getKind()) && "SUCCEEDED".equals(job.getStatus()))
                .count();
        ApprovalPlan.Resume resume = plan.afterApprovalCount(succeeded);
        if (resume == ApprovalPlan.Resume.WAIT) {
            markApprovalWaiting(runJobs, plan.nextApprovalAfterCount(succeeded));
            persistWaiting(runJobs);
            run.setStatus("WAITING_APPROVAL");
            run.setFinishedAt(null);
            run.setErrorMessage(null);
            runMapper.updateById(run);
            return get(pipelineId, runId);
        }
        if (resume == ApprovalPlan.Resume.DONE) {
            run.setStatus("SUCCEEDED");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            return get(pipelineId, runId);
        }
        Integer next = plan.nextSegmentIndex(succeeded);
        if (next == null) {
            run.setStatus("SUCCEEDED");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            return get(pipelineId, runId);
        }
        run.setSegmentIndex(next);
        run.setStatus("QUEUED");
        run.setFinishedAt(null);
        runMapper.updateById(run);
        Long id = run.getId();
        submitAfterCommit(id);
        return get(pipelineId, runId);
    }

    private boolean canSubmitNow(Long tenantId) {
        Long running = runMapper.selectCount(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getTenantId, tenantId)
                .eq(PipelineRunEntity::getStatus, "RUNNING"));
        return running == null || running < maxConcurrentRuns;
    }

    /**
     * 租户维度并发限制 — 从队列中取出下一个等待的运行提交执行。
     * 由 TektonPipelineExecutor 在每次运行结束时调用。
     */
    public void dequeueNextRun(Long tenantId) {
        PipelineRunEntity next = runMapper.selectOne(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getTenantId, tenantId)
                .eq(PipelineRunEntity::getStatus, "QUEUED")
                .orderByAsc(PipelineRunEntity::getId)
                .last("LIMIT 1"));
        if (next == null) {
            return;
        }
        // 重新加载 graph 并提交
        Long pipelineId = next.getPipelineId();
        PipelineGraphSpec graph = definitionService.loadGraph(pipelineId);
        ApprovalPlan plan = ApprovalPlan.of(graph);
        if (plan.segments().isEmpty()) {
            return;
        }
        next.setSegmentIndex(0);
        runMapper.updateById(next);
        submitAfterCommit(next.getId());
    }

    private void submitAfterCommit(Long runId) {
        Runnable submit = () -> {
            try {
                pipelineExecutor.submit(runId);
            } catch (Exception e) {
                failRun(runId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    private static boolean isSameApproval(ApprovalPlan.Item item, PipelineJobEntity job) {
        if (item == null || job == null || !PipelineJobKind.APPROVAL.name().equals(job.getKind())) {
            return false;
        }
        if (item.approvalJobId() != null && job.getId() != null) {
            return item.approvalJobId().equals(job.getId());
        }
        return item.approvalJobName() != null && item.approvalJobName().equals(job.getName());
    }

    private static void markApprovalWaiting(List<PipelineRunJobEntity> runJobs, ApprovalPlan.Item item) {
        if (item == null) {
            return;
        }
        for (PipelineRunJobEntity job : runJobs) {
            if (!PipelineJobKind.APPROVAL.name().equals(job.getKind())) {
                continue;
            }
            boolean idMatch = item.approvalJobId() != null && item.approvalJobId().equals(job.getJobId());
            boolean nameMatch = item.approvalJobId() == null
                    && item.approvalJobName() != null
                    && item.approvalJobName().equals(job.getJobName());
            if (idMatch || nameMatch) {
                job.setStatus("WAITING_APPROVAL");
                job.setFinishedAt(null);
            }
        }
    }

    private void persistWaiting(List<PipelineRunJobEntity> runJobs) {
        for (PipelineRunJobEntity job : runJobs) {
            if ("WAITING_APPROVAL".equals(job.getStatus())) {
                runJobMapper.updateById(job);
            }
        }
    }

    private void failRun(Long runId, String message) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setStatus("FAILED");
        run.setErrorMessage(message);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        List<PipelineRunJobEntity> runJobs = runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                .eq(PipelineRunJobEntity::getRunId, runId));
        for (PipelineRunJobEntity runJob : runJobs) {
            runJob.setStatus("FAILED");
            runJob.setLogText(message);
            runJobMapper.updateById(runJob);
        }
    }

    private PipelineRunVO toVo(Long pipelineId, PipelineRunEntity run, boolean includeJobs) {
        PipelineRunVO vo = new PipelineRunVO();
        vo.setId(run.getId());
        vo.setPipelineId(run.getPipelineId());
        vo.setPipelineName(definitionService.requireOwned(pipelineId).getName());
        vo.setStatus(run.getStatus());
        vo.setTrigger(run.getTrigger());
        vo.setGitRef(run.getGitRef());
        vo.setCommitSha(run.getCommitSha());
        vo.setTriggeredByName(run.getTriggeredByName());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setRuntimeParams(run.getRuntimeParams());
        if (includeJobs) {
            vo.setJobs(runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                            .eq(PipelineRunJobEntity::getRunId, run.getId())
                            .orderByAsc(PipelineRunJobEntity::getId))
                    .stream()
                    .map(this::toJobVo)
                    .toList());
        } else {
            vo.setJobs(List.of());
        }
        return vo;
    }

    // ---- 运行时参数 ----

    /**
     * 写死参数用默认值填入，再覆盖用户在弹框里选的变量。
     */
    private Map<String, String> mergeRuntimeParams(Long pipelineId, Map<String, String> userParams) {
        Map<String, String> merged = new LinkedHashMap<>();
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));
        Map<String, List<PipelineTaskKindParamEntity>> byKind = taskKindParamMapper.selectAll().stream()
                .collect(Collectors.groupingBy(PipelineTaskKindParamEntity::getKindValue));
        for (PipelineJobEntity job : jobs) {
            Map<String, JobParamBindingDTO> bindings = ParamBindings.parse(job.getParamBindings());
            for (PipelineTaskKindParamEntity def : byKind.getOrDefault(job.getKind(), List.of())) {
                JobParamBindingDTO binding = bindings.get(def.getParamKey());
                if (ParamBindings.isRuntime(binding)) {
                    continue;
                }
                if ("GIT_REF".equals(def.getParamKey())) {
                    continue;
                }
                String value = binding != null && binding.getValue() != null
                        ? binding.getValue()
                        : (def.getDefaultValue() != null ? def.getDefaultValue() : "");
                merged.put(def.getParamKey(), value);
            }
        }
        if (userParams != null) {
            merged.putAll(userParams);
        }
        return merged;
    }

    private void validateRuntimeParams(Long pipelineId, Map<String, String> runtimeParams) {
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));
        Map<String, List<PipelineTaskKindParamEntity>> byKind = taskKindParamMapper.selectAll().stream()
                .collect(Collectors.groupingBy(PipelineTaskKindParamEntity::getKindValue));
        for (PipelineJobEntity job : jobs) {
            Map<String, JobParamBindingDTO> bindings = ParamBindings.parse(job.getParamBindings());
            for (PipelineTaskKindParamEntity param : byKind.getOrDefault(job.getKind(), List.of())) {
                if (!ParamBindings.isRuntime(bindings.get(param.getParamKey()))) {
                    continue;
                }
                String key = param.getParamKey();
                boolean isRequired = param.getRequired() != null && param.getRequired() == 1;
                boolean hasValue = runtimeParams.containsKey(key) && runtimeParams.get(key) != null
                        && !runtimeParams.get(key).isBlank();
                if (isRequired && !hasValue) {
                    throw BizException.of(ResultCode.BAD_REQUEST,
                            "运行时参数 [" + param.getParamLabel() + "] 为必填项");
                }
                if (hasValue && "select".equals(param.getParamType())) {
                    List<String> options = parseJsonArray(param.getOptionsJson());
                    if (!options.isEmpty() && !options.contains(runtimeParams.get(key))) {
                        throw BizException.of(ResultCode.BAD_REQUEST,
                                "参数 [" + param.getParamLabel() + "] 的值不在可选范围内");
                    }
                }
            }
        }
    }

    /**
     * 构建 job 的运行命令：
     * - CLONE 类型固定返回 "git clone"
     * - 若有 CMD_ 前缀的运行时参数，替换 command 中的 ${KEY} 占位符
     */
    private static String buildCommand(PipelineJobEntity job, Map<String, String> runtimeParams) {
        if (PipelineJobKind.CLONE.name().equals(job.getKind())) {
            return "git clone";
        }
        String command = job.getCommand();
        if (command == null) {
            return "";
        }
        if (runtimeParams == null || runtimeParams.isEmpty()) {
            return command;
        }
        for (Map.Entry<String, String> entry : runtimeParams.entrySet()) {
            if (entry.getKey().startsWith("CMD_")) {
                String placeholder = "${" + entry.getKey() + "}";
                if (command.contains(placeholder)) {
                    command = command.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
                }
            }
        }
        return command;
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return JSON.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private PipelineRunJobVO toJobVo(PipelineRunJobEntity row) {
        PipelineRunJobVO vo = new PipelineRunJobVO();
        vo.setId(row.getId());
        vo.setJobId(row.getJobId());
        vo.setStageName(row.getStageName());
        vo.setJobName(row.getJobName());
        vo.setKind(row.getKind());
        vo.setCommand(row.getCommand());
        vo.setStatus(row.getStatus());
        vo.setLogText(row.getLogText());
        vo.setPodName(row.getPodName());
        vo.setNamespace(row.getNamespace());
        vo.setContainers(parseContainerJson(row.getContainers()));
        vo.setStartedAt(row.getStartedAt());
        vo.setFinishedAt(row.getFinishedAt());
        return vo;
    }

    private static String[] parseContainerJson(String json) {
        if (json == null || json.isBlank()) return new String[0];
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, String[].class);
        } catch (Exception e) {
            return new String[0];
        }
    }
}
