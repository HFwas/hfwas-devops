package com.hfwas.devops.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.CredentialVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
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
        String username = null;
        String secret = null;
        if (pipeline.getCredentialId() != null) {
            CredentialVO meta = credentialService.get(pipeline.getCredentialId());
            username = meta.getUsername();
            secret = credentialService.decryptSecret(pipeline.getCredentialId());
        }
        CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
                run.getId(),
                pipeline.getRepoUrl(),
                run.getGitRef(),
                run.getImage(),
                secret != null,
                graph
        ));
        ensureNamespace();
        String gitSecretName = compiled.name() + "-git";
        if (secret != null) {
            client.secrets().inNamespace(namespace)
                    .resource(TektonManifests.gitSecret(namespace, gitSecretName, username == null ? "" : username, secret))
                    .serverSideApply();
        }
        if (compiled.mode() == TektonMode.TASK) {
            var task = compiled.tasks().getFirst();
            tekton.v1().tasks().inNamespace(namespace)
                    .resource(TektonManifests.task(namespace, task, secret == null ? null : gitSecretName))
                    .serverSideApply();
            tekton.v1().taskRuns().inNamespace(namespace)
                    .resource(TektonManifests.taskRun(namespace, compiled.name(), task.name()))
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
                    .resource(TektonManifests.pipelineRun(namespace, compiled.name(), compiled.name(), claim))
                    .serverSideApply();
        }
        run.setTektonName(compiled.name());
        run.setStatus("RUNNING");
        runMapper.updateById(run);
        String secretSnapshot = secret;
        Future<?> future = watchPool.submit(() -> watch(runId, compiled, secretSnapshot));
        watches.put(runId, future);
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
        } finally {
            watches.remove(runId);
        }
    }

    private boolean syncTaskRun(Long runId, CompiledTekton compiled, String secret) {
        TaskRun taskRun = tekton.v1().taskRuns().inNamespace(namespace).withName(compiled.name()).get();
        if (taskRun == null) {
            return false;
        }
        List<PipelineRunJobEntity> jobs = loadJobs(runId);
        List<StepState> steps = taskRun.getStatus() == null || taskRun.getStatus().getSteps() == null
                ? List.of()
                : taskRun.getStatus().getSteps();
        String pod = taskRun.getStatus() == null ? null : taskRun.getStatus().getPodName();
        for (int i = 0; i < jobs.size(); i++) {
            StepState step = i < steps.size() ? steps.get(i) : null;
            updateJob(jobs.get(i), step, pod, secret);
        }
        String overall = conditionStatus(taskRun.getStatus() == null ? null : taskRun.getStatus().getConditions());
        if (isTerminal(overall)) {
            markRun(runId, overall, failedMessage(taskRun.getStatus() == null ? null : taskRun.getStatus().getConditions()));
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
        List<PipelineRunJobEntity> jobs = loadJobs(runId);
        for (PipelineRunJobEntity job : jobs) {
            String taskName = DnsNames.stepName(job.getJobName());
            TaskRun child = children.stream()
                    .filter(item -> taskName.equals(label(item, "tekton.dev/pipelineTask")))
                    .findFirst()
                    .orElse(null);
            StepState step = null;
            String pod = null;
            if (child != null && child.getStatus() != null) {
                pod = child.getStatus().getPodName();
                if (child.getStatus().getSteps() != null && !child.getStatus().getSteps().isEmpty()) {
                    step = child.getStatus().getSteps().getFirst();
                }
            }
            updateJob(job, step, pod, secret);
        }
        String overall = conditionStatus(pipelineRun.getStatus() == null ? null : pipelineRun.getStatus().getConditions());
        if (isTerminal(overall)) {
            markRun(runId, overall, failedMessage(pipelineRun.getStatus() == null ? null : pipelineRun.getStatus().getConditions()));
            return true;
        }
        return false;
    }

    private void updateJob(PipelineRunJobEntity job, StepState step, String pod, String secret) {
        job.setStatus(stepStatus(step));
        if (step != null && step.getRunning() != null && job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        if (step != null && step.getTerminated() != null) {
            job.setFinishedAt(LocalDateTime.now());
        }
        if (pod != null && step != null && step.getContainer() != null) {
            try {
                String raw = client.pods().inNamespace(namespace).withName(pod).inContainer(step.getContainer()).getLog();
                job.setLogText(LogMasker.truncate(LogMasker.mask(raw, secret), MAX_LOG_BYTES));
            } catch (Exception ignored) {
                // container may not have started
            }
        }
        runJobMapper.updateById(job);
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
