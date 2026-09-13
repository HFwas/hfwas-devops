package com.hfwas.devops.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindEntity;
import com.hfwas.devops.pipeline.graph.ApprovalPlan;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.service.PipelineCredentialService;
import com.hfwas.devops.pipeline.service.PipelineRunService;
import com.hfwas.devops.pipeline.tekton.CompileRequest;
import com.hfwas.devops.pipeline.tekton.TaskResourceSpec;
import com.hfwas.devops.pipeline.tekton.CompiledTekton;
import com.hfwas.devops.pipeline.tekton.DnsNames;
import com.hfwas.devops.pipeline.tekton.GitHttpProxy;
import com.hfwas.devops.pipeline.tekton.LogMasker;
import com.hfwas.devops.pipeline.tekton.TektonCompiler;
import com.hfwas.devops.pipeline.tekton.TektonManifests;
import com.hfwas.devops.pipeline.tekton.TektonMode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.knative.pkg.apis.Condition;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.StepState;
import io.fabric8.tekton.v1.TaskRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TektonPipelineExecutor implements PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(TektonPipelineExecutor.class);
    private static final int MAX_LOG_BYTES = 512 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final KubernetesClient client;
    private final TektonClient tekton;
    private final String namespace;
    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final PipelineCredentialService credentialService;
    private final PipelineTaskKindMapper taskKindMapper;
    private final PipelineRunService runService;
    private final String gitHttpProxy;
    private final String gitDockerHost;
    private final String apiEndpoint;
    private final ExecutorService watchPool = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "pipeline-tekton-watch");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, Future<?>> watches = new ConcurrentHashMap<>();

    public TektonPipelineExecutor(
            KubernetesClient client,
            String namespace,
            PipelineMapper pipelineMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineCredentialService credentialService,
            PipelineTaskKindMapper taskKindMapper,
            PipelineRunService runService,
            String gitHttpProxy,
            String gitDockerHost,
            String apiEndpoint
    ) {
        this.client = client;
        this.tekton = client.adapt(TektonClient.class);
        this.namespace = namespace;
        this.pipelineMapper = pipelineMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.credentialService = credentialService;
        this.taskKindMapper = taskKindMapper;
        this.runService = runService;
        this.gitHttpProxy = gitHttpProxy;
        this.gitDockerHost = gitDockerHost;
        this.apiEndpoint = apiEndpoint;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void submit(Long runId) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalStateException("运行记录不存在");
        }
        PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
        List<PipelineStageEntity> stages = stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                .eq(PipelineStageEntity::getPipelineId, pipeline.getId())
                .orderByAsc(PipelineStageEntity::getSortOrder));
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipeline.getId()));
        PipelineGraphSpec graph = toGraph(stages, jobs);
        ApprovalPlan plan = ApprovalPlan.of(graph);
        Integer segmentIndex = run.getSegmentIndex();
        if (segmentIndex == null || segmentIndex < 0 || segmentIndex >= plan.segments().size()) {
            throw new IllegalStateException("没有可提交的执行段");
        }
        PipelineGraphSpec segment = plan.segments().get(segmentIndex);
        String username = null;
        String secret = null;
        boolean kubeconfigCredential = false;
        if (pipeline.getCredentialId() != null) {
            CredentialVO meta = credentialService.get(pipeline.getCredentialId());
            username = meta.getUsername();
            secret = credentialService.decryptSecret(pipeline.getCredentialId());
            kubeconfigCredential = "KUBECONFIG".equals(meta.getKind());
            log.info("凭证解析: id={}, name={}, kind={}, username={}, hasSecret={}, kubeconfig={}",
                    pipeline.getCredentialId(), meta.getName(), meta.getKind(),
                    username, secret != null && !secret.isBlank(), kubeconfigCredential);
        } else {
            log.info("流水线 #{} 未关联凭证", pipeline.getCredentialId());
        }
        String dockerHost = GitHttpProxy.dockerHostAddress(gitDockerHost);
        String proxy = GitHttpProxy.rewrite(gitHttpProxy, dockerHost);
        if (proxy != null && !proxy.isBlank()) {
            log.info("clone 使用 git HTTP 代理 {}", proxy);
        }
        // Pod 内 localhost 不可达宿主机 NodePort；改写成 docker 宿主机地址并保留端口
        String repoUrl = GitHttpProxy.rewrite(pipeline.getRepoUrl(), dockerHost);
        if (repoUrl != null && !repoUrl.equals(pipeline.getRepoUrl())) {
            log.info("clone 仓库地址改写 {} -> {}", pipeline.getRepoUrl(), repoUrl);
        }
        // 解析任务镜像：toolImage 优先，其次 defaultImage
        Map<String, String> taskImages = new HashMap<>();
        Map<String, String> taskScripts = new HashMap<>();
        for (PipelineTaskKindEntity kind : taskKindMapper.selectList(null)) {
            String effective = kind.getToolImage();
            if (effective == null || effective.isBlank()) {
                effective = kind.getDefaultImage();
            }
            if (effective != null && !effective.isBlank()) {
                taskImages.put(kind.getKindValue(), effective);
            }
            // 脚本模板
            if (kind.getCommandTemplate() != null && !kind.getCommandTemplate().isBlank()) {
                taskScripts.put(kind.getKindValue(), kind.getCommandTemplate());
            }
        }

        // 解析任务资源配额
        Map<String, TaskResourceSpec> taskResources = new HashMap<>();
        for (PipelineTaskKindEntity kind : taskKindMapper.selectList(null)) {
            taskResources.put(kind.getKindValue(), new TaskResourceSpec(
                    kind.getCpuRequest() != null ? kind.getCpuRequest() : "",
                    kind.getCpuLimit() != null ? kind.getCpuLimit() : "",
                    kind.getMemoryRequest() != null ? kind.getMemoryRequest() : "",
                    kind.getMemoryLimit() != null ? kind.getMemoryLimit() : ""
            ));
        }

        // 集群感知校验：检查 task 资源配置是否超过最大节点容量
        warnIfExceedsNodeCapacity(taskResources);

        // 解析运行时参数
        Map<String, String> runtimeParams = parseRuntimeParams(run.getRuntimeParams());

        CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
                run.getId(),
                pipeline.getId(),
                repoUrl,
                run.getGitRef(),
                secret != null,
                segment,
                proxy,
                taskImages,
                taskScripts,
                taskResources,
                apiEndpoint,
                runtimeParams
        ));
        ensureNamespace();
        String gitSecretName = compiled.name() + "-git";
        String kubeconfigSecretName = compiled.name() + "-kube";
        if (secret != null) {
            if (kubeconfigCredential) {
                client.secrets().inNamespace(namespace)
                        .resource(TektonManifests.kubeconfigSecret(namespace, kubeconfigSecretName, secret))
                        .serverSideApply();
            } else {
                client.secrets().inNamespace(namespace)
                        .resource(TektonManifests.gitSecret(namespace, gitSecretName, username == null ? "" : username, secret))
                        .serverSideApply();
            }
        }
        String cacheClaim = null;
        String gitSecretEffective = (secret != null && !kubeconfigCredential) ? gitSecretName : null;
        String kubeconfigSecretEffective = (secret != null && kubeconfigCredential) ? kubeconfigSecretName : null;
        log.info("Tekton 任务凭证注入: gitSecret={}, kubeconfigSecret={}", gitSecretEffective, kubeconfigSecretEffective);
        if (compiled.mode() == TektonMode.TASK) {
            var task = compiled.tasks().getFirst();
            tekton.v1().tasks().inNamespace(namespace)
                    .resource(TektonManifests.task(namespace, task, gitSecretEffective, kubeconfigSecretEffective))
                    .serverSideApply();
            tekton.v1().taskRuns().inNamespace(namespace)
                    .resource(TektonManifests.taskRun(namespace, compiled.name(), task.name()))
                    .serverSideApply();
        } else {
            String claim = compiled.name() + "-ws";
            PersistentVolumeClaim pvc = TektonManifests.workspaceClaim(namespace, claim);
            try {
                client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
            } catch (Exception e) {
                // PVC 可能已存在（重试/同名），替换之
                client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).update();
            }
            for (var task : compiled.tasks()) {
                tekton.v1().tasks().inNamespace(namespace)
                        .resource(TektonManifests.task(namespace, task, gitSecretEffective, kubeconfigSecretEffective))
                        .serverSideApply();
            }
            tekton.v1().pipelines().inNamespace(namespace)
                    .resource(TektonManifests.pipeline(namespace, compiled))
                    .serverSideApply();
            tekton.v1().pipelineRuns().inNamespace(namespace)
                    .resource(TektonManifests.pipelineRun(namespace, compiled.name(), compiled.name(), claim))
                    .serverSideApply();
        }
        run.setTektonName(compiled.name());
        run.setStatus("RUNNING");
        runMapper.updateById(run);
        String secretSnapshot = secret;
        Future<?>[] holder = new Future<?>[1];
        holder[0] = watchPool.submit(() -> {
            try {
                watch(runId, compiled, secretSnapshot);
            } finally {
                watches.remove(runId, holder[0]);
            }
        });
        watches.put(runId, holder[0]);
    }

    @Override
    public void cancel(Long runId) {
        Future<?> future = watches.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null || run.getTektonName() == null) {
            return;
        }
        String name = run.getTektonName();
        TaskRun taskRun = tekton.v1().taskRuns().inNamespace(namespace).withName(name).get();
        if (taskRun != null && taskRun.getSpec() != null) {
            taskRun.getSpec().setStatus("TaskRunCancelled");
            tekton.v1().taskRuns().inNamespace(namespace).resource(taskRun).update();
        }
        PipelineRun pipelineRun = tekton.v1().pipelineRuns().inNamespace(namespace).withName(name).get();
        if (pipelineRun != null && pipelineRun.getSpec() != null) {
            pipelineRun.getSpec().setStatus("Cancelled");
            tekton.v1().pipelineRuns().inNamespace(namespace).resource(pipelineRun).update();
        }
    }

    private void watch(Long runId, CompiledTekton compiled, String secret) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                boolean done = compiled.mode() == TektonMode.TASK
                        ? syncTaskRun(runId, compiled, secret)
                        : syncPipelineRun(runId, compiled, secret);
                if (done) {
                    return;
                }
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("watch pipeline run {} failed: {}", runId, e.getMessage());
            markRun(runId, "FAILED", e.getMessage());
        }
    }

    private boolean syncTaskRun(Long runId, CompiledTekton compiled, String secret) {
        TaskRun taskRun = tekton.v1().taskRuns().inNamespace(namespace).withName(compiled.name()).get();
        if (taskRun == null) {
            return false;
        }
        List<StepState> steps = taskRun.getStatus() == null ? List.of() : taskRun.getStatus().getSteps();
        String pod = taskRun.getStatus() == null ? null : taskRun.getStatus().getPodName();
        List<String> extraMask = extraSecrets(runId);
        List<PipelineRunJobEntity> jobs = loadJobs(runId);
        List<String> assigned = assignedStepNames(jobs);
        boolean priorOpen = false;
        for (int i = 0; i < jobs.size(); i++) {
            PipelineRunJobEntity job = jobs.get(i);
            String assignedName = assigned.get(i);
            if (assignedName == null || assignedName.isBlank()) {
                continue;
            }
            if (priorOpen) {
                keepQueued(job);
                continue;
            }
            List<StepState> matched = matchSteps(assignedName, assigned, steps);
            if (!matched.isEmpty()) {
                updateJob(job, matched, pod, secret, extraMask);
            }
            if (!isTerminal(job.getStatus())) {
                priorOpen = true;
            }
        }
        String overall = conditionStatus(taskRun.getStatus() == null ? null : taskRun.getStatus().getConditions());
        if (isTerminal(overall)) {
            finishSegment(runId, compiled, overall, failedMessage(taskRun.getStatus() == null ? null : taskRun.getStatus().getConditions()));
            return true;
        }
        return false;
    }

    private boolean syncPipelineRun(Long runId, CompiledTekton compiled, String secret) {
        PipelineRun pipelineRun = tekton.v1().pipelineRuns().inNamespace(namespace).withName(compiled.name()).get();
        if (pipelineRun == null) {
            return false;
        }
        List<TaskRun> children = tekton.v1().taskRuns().inNamespace(namespace)
                .withLabel("tekton.dev/pipelineRun", compiled.name())
                .list()
                .getItems();
        List<String> extraMask = extraSecrets(runId);
        List<PipelineRunJobEntity> jobs = loadJobs(runId);
        List<String> assigned = assignedStepNames(jobs);
        for (int i = 0; i < jobs.size(); i++) {
            PipelineRunJobEntity job = jobs.get(i);
            String taskName = assigned.get(i);
            if (taskName == null || taskName.isBlank()) {
                continue;
            }
            TaskRun child = children.stream()
                    .filter(item -> taskName.equals(label(item, "tekton.dev/pipelineTask")))
                    .findFirst()
                    .orElse(null);
            if (child == null || child.getStatus() == null) {
                continue;
            }
            List<StepState> steps = child.getStatus().getSteps();
            if (steps == null || steps.isEmpty()) {
                continue;
            }
            updateJob(job, steps, child.getStatus().getPodName(), secret, extraMask);
        }
        String overall = conditionStatus(pipelineRun.getStatus() == null ? null : pipelineRun.getStatus().getConditions());
        if (isTerminal(overall)) {
            finishSegment(runId, compiled, overall, failedMessage(pipelineRun.getStatus() == null ? null : pipelineRun.getStatus().getConditions()));
            return true;
        }
        return false;
    }

    private static List<String> assignedStepNames(List<PipelineRunJobEntity> jobs) {
        return DnsNames.assignJobStepNames(
                jobs.stream().map(PipelineRunJobEntity::getJobName).toList(),
                jobs.stream().map(PipelineRunJobEntity::getKind).toList());
    }

    private static List<StepState> matchSteps(String assigned, List<String> allAssigned, List<StepState> steps) {
        if (steps == null || steps.isEmpty() || assigned == null || assigned.isBlank()) {
            return List.of();
        }
        return steps.stream()
                .filter(step -> DnsNames.stepBelongsTo(step.getName(), assigned, allAssigned))
                .toList();
    }

    private void keepQueued(PipelineRunJobEntity job) {
        if ("QUEUED".equals(job.getStatus()) && job.getStartedAt() == null && job.getFinishedAt() == null) {
            return;
        }
        job.setStatus("QUEUED");
        job.setStartedAt(null);
        job.setFinishedAt(null);
        runJobMapper.updateById(job);
    }

    private void updateJob(PipelineRunJobEntity job, List<StepState> steps, String pod, String secret, List<String> extraSecrets) {
        String status = mergeStepStatus(steps);
        job.setStatus(status);
        job.setStartedAt(resolveJobStartedAt(status, steps, job.getStartedAt()));
        job.setFinishedAt(resolveJobFinishedAt(status, steps, job.getFinishedAt()));
        // 首次拿到 podName 时写入 pod/namespace/containers（后续不再覆盖）
        if (pod != null && !pod.isBlank() && job.getPodName() == null) {
            job.setPodName(pod);
            job.setNamespace(namespace);
            List<String> containerNames = steps.stream()
                    .map(StepState::getContainer)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .toList();
            job.setContainers(toContainerJson(containerNames));
        }
        if (pod != null && !pod.isBlank()) {
            StringBuilder logs = new StringBuilder();
            for (StepState step : steps) {
                if (step.getContainer() == null) {
                    continue;
                }
                try {
                    String raw = client.pods().inNamespace(namespace).withName(pod)
                            .inContainer(step.getContainer()).getLog();
                    if (raw != null && !raw.isBlank()) {
                        if (!logs.isEmpty()) {
                            logs.append("\n--- ").append(step.getName()).append(" ---\n");
                        }
                        logs.append(raw);
                    }
                } catch (Exception ignored) {
                    // 容器可能还没起来；下面用 waiting/terminated 诊断补日志
                }
            }
            if (logs.isEmpty()) {
                String diagnostics = stepDiagnostics(steps);
                if (diagnostics.isBlank()) {
                    diagnostics = podWaitingMessage(pod);
                }
                if (!diagnostics.isBlank()) {
                    logs.append(diagnostics);
                }
            }
            if (!logs.isEmpty()) {
                String masked = LogMasker.mask(logs.toString(), secret);
                for (String extra : extraSecrets) {
                    masked = LogMasker.mask(masked, extra);
                }
                job.setLogText(LogMasker.truncate(masked, MAX_LOG_BYTES));
            }
        }
        runJobMapper.updateById(job);
        captureCloneCommit(job);
    }

    private void captureCloneCommit(PipelineRunJobEntity job) {
        if (!"CLONE".equals(job.getKind())) {
            return;
        }
        String sha = DnsNames.parseCommitSha(job.getLogText());
        if (sha == null || job.getRunId() == null) {
            return;
        }
        PipelineRunEntity run = runMapper.selectById(job.getRunId());
        if (run == null) {
            return;
        }
        if (run.getCommitSha() != null && !run.getCommitSha().isBlank()) {
            return;
        }
        run.setCommitSha(sha);
        runMapper.updateById(run);
    }

    static String mergeStepStatus(List<StepState> steps) {
        if (steps == null || steps.isEmpty()) {
            return "QUEUED";
        }
        boolean anyFailed = false;
        boolean anyRunning = false;
        boolean anyQueued = false;
        boolean anySucceeded = false;
        for (StepState step : steps) {
            String status = stepStatus(step);
            if ("FAILED".equals(status)) {
                anyFailed = true;
            } else if ("RUNNING".equals(status)) {
                anyRunning = true;
            } else if ("SUCCEEDED".equals(status)) {
                anySucceeded = true;
            } else {
                anyQueued = true;
            }
        }
        if (anyFailed) {
            return "FAILED";
        }
        if (anyRunning) {
            return "RUNNING";
        }
        if (anyQueued) {
            return "QUEUED";
        }
        if (anySucceeded) {
            return "SUCCEEDED";
        }
        return "QUEUED";
    }

    /** 排队中无耗时；否则取 step 真实 startedAt，缺省时保留已有值。 */
    static LocalDateTime resolveJobStartedAt(String status, List<StepState> steps, LocalDateTime previous) {
        if ("QUEUED".equals(status)) {
            return null;
        }
        LocalDateTime fromSteps = earliestStartedAt(steps);
        if (fromSteps != null) {
            return fromSteps;
        }
        return previous != null ? previous : nowUtc();
    }

    /** 全部 terminated 后用 step finishedAt；禁止每次 poll 用 now() 把已结束任务的结束时间往后推。 */
    static LocalDateTime resolveJobFinishedAt(String status, List<StepState> steps, LocalDateTime previous) {
        if ("QUEUED".equals(status)) {
            return null;
        }
        boolean allTerminated = steps != null && !steps.isEmpty()
                && steps.stream().allMatch(step -> step.getTerminated() != null);
        if (allTerminated) {
            LocalDateTime fromSteps = latestFinishedAt(steps);
            if (fromSteps != null) {
                return fromSteps;
            }
            return previous != null ? previous : nowUtc();
        }
        if (isTerminal(status)) {
            return previous != null ? previous : nowUtc();
        }
        return null;
    }

    static LocalDateTime earliestStartedAt(List<StepState> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        LocalDateTime earliest = null;
        for (StepState step : steps) {
            LocalDateTime started = stepStartedAt(step);
            if (started != null && (earliest == null || started.isBefore(earliest))) {
                earliest = started;
            }
        }
        return earliest;
    }

    static LocalDateTime latestFinishedAt(List<StepState> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        LocalDateTime latest = null;
        for (StepState step : steps) {
            if (step.getTerminated() == null) {
                return null;
            }
            LocalDateTime finished = parseK8sTime(step.getTerminated().getFinishedAt());
            if (finished == null) {
                return null;
            }
            if (latest == null || finished.isAfter(latest)) {
                latest = finished;
            }
        }
        return latest;
    }

    private static LocalDateTime stepStartedAt(StepState step) {
        if (step.getRunning() != null) {
            LocalDateTime started = parseK8sTime(step.getRunning().getStartedAt());
            if (started != null) {
                return started;
            }
        }
        if (step.getTerminated() != null) {
            return parseK8sTime(step.getTerminated().getStartedAt());
        }
        return null;
    }

    static LocalDateTime parseK8sTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(value).toLocalDateTime();
            }
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (DateTimeException ignored) {
            try {
                return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeException ignoredToo) {
                return null;
            }
        }
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private List<String> extraSecrets(Long runId) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            return List.of();
        }
        List<String> extras = new ArrayList<>();
        for (PipelineJobEntity job : jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, run.getPipelineId()))) {
            String command = job.getCommand();
            if (command == null) {
                continue;
            }
            addEnv(extras, command, "SONAR_TOKEN");
            addEnv(extras, command, "COSIGN_PRIVATE_KEY");
            addEnv(extras, command, "S3_SECRET_KEY");
        }
        return extras;
    }

    private static void addEnv(List<String> extras, String command, String key) {
        String needle = key + "=";
        int i = command.indexOf(needle);
        if (i < 0) {
            return;
        }
        int start = i + needle.length();
        if (start < command.length() && (command.charAt(start) == '"' || command.charAt(start) == '\'')) {
            char quote = command.charAt(start);
            start++;
            int end = command.indexOf(quote, start);
            if (end < 0) {
                return;
            }
            addSecret(extras, command.substring(start, end));
            return;
        }
        int end = start;
        while (end < command.length() && !Character.isWhitespace(command.charAt(end)) && command.charAt(end) != ';') {
            end++;
        }
        addSecret(extras, command.substring(start, end));
    }

    private static void addSecret(List<String> extras, String value) {
        if (value != null && !value.isBlank()) {
            extras.add(value);
        }
    }

    private void finishSegment(Long runId, CompiledTekton compiled, String overall, String message) {
        if (!"SUCCEEDED".equals(overall)) {
            markRun(runId, overall, message);
            dequeueNextRun(runId);
            return;
        }
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
        ApprovalPlan plan = ApprovalPlan.of(toGraph(
                stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                        .eq(PipelineStageEntity::getPipelineId, pipeline.getId())
                        .orderByAsc(PipelineStageEntity::getSortOrder)),
                jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                        .eq(PipelineJobEntity::getPipelineId, pipeline.getId()))));
        int idx = run.getSegmentIndex() == null ? 0 : run.getSegmentIndex();
        ApprovalPlan.Resume resume = plan.afterSegment(idx);
        if (resume == ApprovalPlan.Resume.DONE) {
            markRun(runId, "SUCCEEDED", null);
            dequeueNextRun(runId);
            return;
        }
        if (resume == ApprovalPlan.Resume.SUBMIT) {
            Integer next = plan.nextSegmentAfterSegment(idx);
            if (next == null) {
                markRun(runId, "SUCCEEDED", null);
                dequeueNextRun(runId);
                return;
            }
            run.setSegmentIndex(next);
            run.setStatus("QUEUED");
            run.setFinishedAt(null);
            run.setErrorMessage(null);
            runMapper.updateById(run);
            submit(runId);
            return;
        }
        run.setStatus("WAITING_APPROVAL");
        run.setFinishedAt(null);
        run.setErrorMessage(null);
        runMapper.updateById(run);
        markNextApproval(runId, plan.nextApprovalAfterSegment(idx));
    }

    private void dequeueNextRun(Long finishedRunId) {
        PipelineRunEntity run = runMapper.selectById(finishedRunId);
        if (run == null || run.getTenantId() == null) {
            return;
        }
        runService.dequeueNextRun(run.getTenantId());
    }

    private void markNextApproval(Long runId, ApprovalPlan.Item item) {
        if (item == null) {
            return;
        }
        for (PipelineRunJobEntity job : loadJobs(runId)) {
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
                runJobMapper.updateById(job);
            }
        }
    }

    private static String stepStatus(StepState step) {
        if (step == null) {
            return "QUEUED";
        }
        if (step.getTerminated() != null) {
            Integer code = step.getTerminated().getExitCode();
            return code != null && code == 0 ? "SUCCEEDED" : "FAILED";
        }
        if (step.getRunning() != null) {
            return "RUNNING";
        }
        return "QUEUED";
    }

    private static String conditionStatus(List<Condition> conditions) {
        if (conditions == null) {
            return "RUNNING";
        }
        for (Condition condition : conditions) {
            if (!"Succeeded".equals(condition.getType())) {
                continue;
            }
            if ("True".equals(condition.getStatus())) {
                return "SUCCEEDED";
            }
            if ("False".equals(condition.getStatus())) {
                String reason = condition.getReason() == null ? "" : condition.getReason();
                if (reason.toLowerCase().contains("cancel")) {
                    return "CANCELLED";
                }
                return "FAILED";
            }
        }
        return "RUNNING";
    }

    private static String failedMessage(List<Condition> conditions) {
        if (conditions == null) {
            return null;
        }
        return conditions.stream()
                .filter(item -> "Succeeded".equals(item.getType()) && "False".equals(item.getStatus()))
                .map(Condition::getMessage)
                .findFirst()
                .orElse(null);
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private static String label(TaskRun run, String key) {
        if (run.getMetadata() == null || run.getMetadata().getLabels() == null) {
            return null;
        }
        return run.getMetadata().getLabels().get(key);
    }

    private void markRun(Long runId, String status, String error) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setStatus(status);
        run.setErrorMessage(error);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            List<PipelineRunJobEntity> jobs = loadJobs(runId);
            for (PipelineRunJobEntity job : jobs) {
                boolean changed = false;
                if (!isTerminal(job.getStatus())) {
                    job.setStatus(status);
                    job.setFinishedAt(LocalDateTime.now());
                    changed = true;
                }
                if ((job.getLogText() == null || job.getLogText().isBlank()) && error != null) {
                    job.setLogText(error);
                    changed = true;
                }
                if (changed) {
                    runJobMapper.updateById(job);
                }
            }
        }
    }

    private List<PipelineRunJobEntity> loadJobs(Long runId) {
        return runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                .eq(PipelineRunJobEntity::getRunId, runId)
                .orderByAsc(PipelineRunJobEntity::getId));
    }

    private String podWaitingMessage(String pod) {
        try {
            var resource = client.pods().inNamespace(namespace).withName(pod).get();
            if (resource == null || resource.getStatus() == null || resource.getStatus().getContainerStatuses() == null) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (var status : resource.getStatus().getContainerStatuses()) {
                var waiting = status.getState() == null ? null : status.getState().getWaiting();
                if (waiting == null) {
                    continue;
                }
                out.append(status.getName()).append(": ");
                if (waiting.getReason() != null) {
                    out.append(waiting.getReason()).append(' ');
                }
                if (waiting.getMessage() != null) {
                    out.append(waiting.getMessage());
                }
                out.append('\n');
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    static String stepDiagnostics(List<StepState> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (StepState step : steps) {
            String name = step.getName() == null ? "step" : step.getName();
            if (step.getWaiting() != null) {
                out.append(name).append(": waiting");
                if (step.getWaiting().getReason() != null) {
                    out.append(' ').append(step.getWaiting().getReason());
                }
                if (step.getWaiting().getMessage() != null) {
                    out.append('\n').append(step.getWaiting().getMessage());
                }
                out.append('\n');
            }
            if (step.getTerminated() != null) {
                var terminated = step.getTerminated();
                out.append(name).append(": terminated");
                if (terminated.getReason() != null) {
                    out.append(' ').append(terminated.getReason());
                }
                if (terminated.getExitCode() != null) {
                    out.append(" exit=").append(terminated.getExitCode());
                }
                if (terminated.getMessage() != null) {
                    out.append('\n').append(terminated.getMessage());
                }
                out.append('\n');
            }
        }
        return out.toString().trim();
    }

    private void ensureNamespace() {
        if (client.namespaces().withName(namespace).get() == null) {
            client.namespaces().resource(new NamespaceBuilder()
                            .withNewMetadata().withName(namespace).endMetadata()
                            .build())
                    .create();
        }
    }

    private static PipelineGraphSpec toGraph(List<PipelineStageEntity> stages, List<PipelineJobEntity> jobs) {
        List<PipelineStageSpec> specs = new ArrayList<>();
        for (PipelineStageEntity stage : stages) {
            List<PipelineJobSpec> stageJobs = jobs.stream()
                    .filter(job -> stage.getId().equals(job.getStageId()))
                    .sorted(Comparator.comparingInt(job -> job.getSortOrder() == null ? 0 : job.getSortOrder()))
                    .map(job -> new PipelineJobSpec(
                            job.getId(),
                            job.getName(),
                            PipelineJobKind.valueOf(job.getKind()),
                            job.getCommand(),
                            job.getStack(),
                            job.getRuntimeVersion(),
                            job.getToolVersion(),
                            job.getSortOrder() == null ? 0 : job.getSortOrder()))
                    .toList();
            specs.add(new PipelineStageSpec(stage.getId(), stage.getName(), stage.getSortOrder(), stageJobs));
        }
        return new PipelineGraphSpec(specs);
    }

    // ---- Pod Exec support ----

    private static final com.fasterxml.jackson.databind.ObjectMapper CONTAINER_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    static String toContainerJson(List<String> names) {
        try {
            return CONTAINER_MAPPER.writeValueAsString(names);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 解析运行时参数 JSON，返回 key-value map。
     */
    private static Map<String, String> parseRuntimeParams(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("parse runtime params failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 校验任务资源配置是否超过集群中最大节点的可分配容量。
     * 超过时只打 warn 日志，不阻止运行 — 最终由 K8s 调度器决策。
     */
    private void warnIfExceedsNodeCapacity(Map<String, TaskResourceSpec> taskResources) {
        if (taskResources == null || taskResources.isEmpty()) return;
        try {
            double maxNodeCpu = 0;
            long maxNodeMemory = 0;
            var nodes = client.nodes().list();
            if (nodes == null || nodes.getItems() == null || nodes.getItems().isEmpty()) return;
            for (var node : nodes.getItems()) {
                var alloc = node.getStatus() != null ? node.getStatus().getAllocatable() : null;
                if (alloc == null) continue;
                if (alloc.get("cpu") != null) {
                    double c = alloc.get("cpu").getNumericalAmount().doubleValue();
                    if (c > maxNodeCpu) maxNodeCpu = c;
                }
                if (alloc.get("memory") != null) {
                    long m = alloc.get("memory").getNumericalAmount().longValue();
                    if (m > maxNodeMemory) maxNodeMemory = m;
                }
            }
            if (maxNodeCpu <= 0 && maxNodeMemory <= 0) return;
            for (var entry : taskResources.entrySet()) {
                String kind = entry.getKey();
                TaskResourceSpec spec = entry.getValue();
                if (spec.cpuLimit() != null && !spec.cpuLimit().isBlank() && maxNodeCpu > 0) {
                    try {
                        double val = new Quantity(spec.cpuLimit()).getNumericalAmount().doubleValue();
                        if (val > maxNodeCpu) {
                            log.warn("[{}] cpuLimit={} 超过集群最大节点可分配 CPU={}，Pod 可能无法调度", kind, spec.cpuLimit(), maxNodeCpu);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (spec.memoryLimit() != null && !spec.memoryLimit().isBlank() && maxNodeMemory > 0) {
                    try {
                        long val = new Quantity(spec.memoryLimit()).getNumericalAmount().longValue();
                        if (val > maxNodeMemory) {
                            log.warn("[{}] memoryLimit={} 超过集群最大节点可分配内存={}，Pod 可能无法调度", kind, spec.memoryLimit(), readableBytes(maxNodeMemory));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("检查节点容量失败: {}", e.getMessage());
        }
    }

    private static String readableBytes(long bytes) {
        if (bytes >= 1L << 30) return (bytes >> 30) + "Gi";
        if (bytes >= 1L << 20) return (bytes >> 20) + "Mi";
        return bytes + "";
    }
}
