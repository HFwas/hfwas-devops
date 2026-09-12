package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelinePageQuery;
import com.hfwas.devops.pipeline.dto.PipelineRunJobVO;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.graph.ApprovalPlan;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PipelineRunService {

    private final PipelineDefinitionService definitionService;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final CurrentUserAccessor currentUserAccessor;
    private final PipelineExecutor pipelineExecutor;
    private final int maxConcurrentRuns;

    public PipelineRunService(
            PipelineDefinitionService definitionService,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            CurrentUserAccessor currentUserAccessor,
            @Lazy PipelineExecutor pipelineExecutor,
            @Value("${pipeline.max-concurrent-runs:10}") int maxConcurrentRuns
    ) {
        this.definitionService = definitionService;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.currentUserAccessor = currentUserAccessor;
        this.pipelineExecutor = pipelineExecutor;
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    @Transactional
    public PipelineRunVO start(Long pipelineId) {
        PipelineEntity pipeline = definitionService.requireOwned(pipelineId);
        List<PipelineStageEntity> stages = stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                .eq(PipelineStageEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineStageEntity::getSortOrder));
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));

        boolean clusterReady = pipelineExecutor.isReady();
        PipelineGraphSpec graph = definitionService.loadGraph(pipelineId);
        ApprovalPlan plan = ApprovalPlan.of(graph);
        boolean waitFirst = clusterReady && plan.waitBeforeFirst();
        String status = !clusterReady ? "FAILED" : (waitFirst ? "WAITING_APPROVAL" : "QUEUED");
        String error = clusterReady ? null : "未配置执行集群（pipeline.kubeconfig），定义已保存，暂不能真正执行";

        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(pipelineId);
        run.setTenantId(pipeline.getTenantId());
        run.setStatus(status);
        run.setTrigger("MANUAL");
        run.setGitRef(pipeline.getGitRef());
        run.setTriggeredByName(currentUserAccessor.currentDisplayName());
        run.setErrorMessage(error);
        run.setStartedAt(LocalDateTime.now());
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
                String command = job.getKind().equals(PipelineJobKind.CLONE.name())
                        ? "git clone"
                        : job.getCommand();
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
