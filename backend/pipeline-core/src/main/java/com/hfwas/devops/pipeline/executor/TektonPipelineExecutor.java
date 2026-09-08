package com.hfwas.devops.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.graph.ApprovalPlan;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.service.PipelineCredentialService;
import com.hfwas.devops.pipeline.tekton.CompileRequest;
import com.hfwas.devops.pipeline.tekton.CompiledTekton;
import com.hfwas.devops.pipeline.tekton.DnsNames;
import com.hfwas.devops.pipeline.tekton.LogMasker;
import com.hfwas.devops.pipeline.tekton.TektonCompiler;
import com.hfwas.devops.pipeline.tekton.TektonManifests;
import com.hfwas.devops.pipeline.tekton.TektonMode;
import io.fabric8.knative.pkg.apis.Condition;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.StepState;
import io.fabric8.tekton.v1.TaskRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TektonPipelineExecutor implements PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(TektonPipelineExecutor.class);
    private static final int MAX_LOG_BYTES = 512 * 1024;

    private final KubernetesClient client;
    private final TektonClient tekton;
    private final String namespace;
    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final PipelineCredentialService credentialService;
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
            PipelineCredentialService credentialService
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
        if (pipeline.getCredentialId() != null) {
            CredentialVO meta = credentialService.get(pipeline.getCredentialId());
            username = meta.getUsername();
            secret = credentialService.decryptSecret(pipeline.getCredentialId());
        }
        CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
                run.getId(),
                pipeline.getId(),
                pipeline.getRepoUrl(),
                run.getGitRef(),
                run.getImage(),
                secret != null,
                segment
        ));
        ensureNamespace();
        String gitSecretName = compiled.name() + "-git";
        if (secret != null) {
            client.secrets().inNamespace(namespace)
                    .resource(TektonManifests.gitSecret(namespace, gitSecretName, username == null ? "" : username, secret))
                    .serverSideApply();
        }
        String cacheClaim = compiled.anyKanikoCache() ? DnsNames.kanikoCache(pipeline.getId()) : null;
        if (cacheClaim != null) {
            client.persistentVolumeClaims().inNamespace(namespace)
                    .resource(TektonManifests.kanikoCacheClaim(namespace, cacheClaim))
                    .serverSideApply();
        }
        if (compiled.mode() == TektonMode.TASK) {
            var task = compiled.tasks().getFirst();
            tekton.v1().tasks().inNamespace(namespace)
                    .resource(TektonManifests.task(namespace, task, secret == null ? null : gitSecretName))
                    .serverSideApply();
            tekton.v1().taskRuns().inNamespace(namespace)
                    .resource(TektonManifests.taskRun(namespace, compiled.name(), task.name(), cacheClaim))
                    .serverSideApply();
        } else {
            String claim = compiled.name() + "-ws";
            client.persistentVolumeClaims().inNamespace(namespace)
                    .resource(TektonManifests.workspaceClaim(namespace, claim))
                    .serverSideApply();
            for (var task : compiled.tasks()) {
                tekton.v1().tasks().inNamespace(namespace)
                        .resource(TektonManifests.task(namespace, task, secret == null ? null : gitSecretName))
                        .serverSideApply();
            }
            tekton.v1().pipelines().inNamespace(namespace)
                    .resource(TektonManifests.pipeline(namespace, compiled))
                    .serverSideApply();
            tekton.v1().pipelineRuns().inNamespace(namespace)
                    .resource(TektonManifests.pipelineRun(namespace, compiled.name(), compiled.name(), claim, cacheClaim))
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
        for (PipelineRunJobEntity job : loadJobs(runId)) {
            List<StepState> matched = matchSteps(job, steps);
            if (!matched.isEmpty()) {
                updateJob(job, matched, pod, secret, extraMask);
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
        for (PipelineRunJobEntity job : loadJobs(runId)) {
            String taskName = DnsNames.stepName(job.getJobName());
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

    private List<StepState> matchSteps(PipelineRunJobEntity job, List<StepState> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        String prefix = DnsNames.stepName(job.getJobName());
        return steps.stream()
                .filter(step -> step.getName() != null
                        && (step.getName().equals(prefix) || step.getName().startsWith(prefix + "-")))
                .toList();
    }

    private void updateJob(PipelineRunJobEntity job, List<StepState> steps, String pod, String secret, List<String> extraSecrets) {
        job.setStatus(mergeStepStatus(steps));
        boolean anyRunning = steps.stream().anyMatch(step -> step.getRunning() != null);
        boolean anyTerminated = steps.stream().anyMatch(step -> step.getTerminated() != null);
        boolean allTerminated = !steps.isEmpty() && steps.stream().allMatch(step -> step.getTerminated() != null);
        if ((anyRunning || anyTerminated) && job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        if (allTerminated) {
            job.setFinishedAt(LocalDateTime.now());
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
                    // container may not have started
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
    }

    private static String mergeStepStatus(List<StepState> steps) {
        boolean anyFailed = false;
        boolean anyRunning = false;
        boolean allSucceeded = true;
        for (StepState step : steps) {
            String status = stepStatus(step);
            if ("FAILED".equals(status)) {
                anyFailed = true;
                allSucceeded = false;
            } else if ("RUNNING".equals(status) || "QUEUED".equals(status)) {
                anyRunning = true;
                allSucceeded = false;
            }
        }
        if (anyFailed) {
            return "FAILED";
        }
        if (anyRunning) {
            return "RUNNING";
        }
        if (allSucceeded) {
            return "SUCCEEDED";
        }
        return "RUNNING";
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
            return;
        }
        if (resume == ApprovalPlan.Resume.SUBMIT) {
            Integer next = plan.nextSegmentAfterSegment(idx);
            if (next == null) {
                markRun(runId, "SUCCEEDED", null);
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
                if (!isTerminal(job.getStatus())) {
                    job.setStatus(status);
                    job.setFinishedAt(LocalDateTime.now());
                    if (job.getLogText() == null && error != null) {
                        job.setLogText(error);
                    }
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
                            job.getSortOrder() == null ? 0 : job.getSortOrder()))
                    .toList();
            specs.add(new PipelineStageSpec(stage.getId(), stage.getName(), stage.getSortOrder(), stageJobs));
        }
        return new PipelineGraphSpec(specs);
    }
}
