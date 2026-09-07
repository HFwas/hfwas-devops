package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineRunJobVO;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.toolchain.PipelineStack;
import com.hfwas.devops.pipeline.toolchain.ToolchainCatalog;
import com.hfwas.devops.pipeline.toolchain.ToolchainResolved;
import com.hfwas.devops.user.context.CurrentUserAccessor;
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
    private final ToolchainCatalog toolchainCatalog = new ToolchainCatalog();
    private final CurrentUserAccessor currentUserAccessor;
    private final PipelineExecutor pipelineExecutor;

    public PipelineRunService(
            PipelineDefinitionService definitionService,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            CurrentUserAccessor currentUserAccessor,
            PipelineExecutor pipelineExecutor
    ) {
        this.definitionService = definitionService;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.currentUserAccessor = currentUserAccessor;
        this.pipelineExecutor = pipelineExecutor;
    }

    @Transactional
    public PipelineRunVO start(Long pipelineId) {
        PipelineEntity pipeline = definitionService.requireOwned(pipelineId);
        ToolchainResolved resolved = toolchainCatalog.resolve(
                PipelineStack.valueOf(pipeline.getStack()),
                pipeline.getRuntimeVersion(),
                pipeline.getToolVersion());
        List<PipelineStageEntity> stages = stageMapper.selectList(new LambdaQueryWrapper<PipelineStageEntity>()
                .eq(PipelineStageEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineStageEntity::getSortOrder));
        List<PipelineJobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<PipelineJobEntity>()
                .eq(PipelineJobEntity::getPipelineId, pipelineId)
                .orderByAsc(PipelineJobEntity::getSortOrder));

        boolean clusterReady = pipelineExecutor.isReady();
        String status = clusterReady ? "QUEUED" : "FAILED";
        String error = clusterReady ? null : "未配置执行集群（pipeline.kubeconfig），定义已保存，暂不能真正执行";

        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(pipelineId);
        run.setTenantId(pipeline.getTenantId());
        run.setStatus(status);
        run.setTrigger("MANUAL");
        run.setGitRef(pipeline.getGitRef());
        run.setStack(pipeline.getStack());
        run.setRuntimeVersion(pipeline.getRuntimeVersion());
        run.setToolVersion(pipeline.getToolVersion());
        run.setImage(resolved.image());
        run.setErrorMessage(error);
        run.setStartedAt(LocalDateTime.now());
        if (!clusterReady) {
            run.setFinishedAt(LocalDateTime.now());
        }
        run.setCreateBy(currentUserAccessor.currentUserId());
        runMapper.insert(run);

        for (PipelineJobEntity job : jobs) {
            PipelineStageEntity stage = stages.stream()
                    .filter(item -> item.getId().equals(job.getStageId()))
                    .findFirst()
                    .orElse(null);
            String command = job.getKind().equals(PipelineJobKind.CLONE.name())
                    ? "git clone"
                    : job.getCommand();
            PipelineRunJobEntity runJob = new PipelineRunJobEntity();
            runJob.setRunId(run.getId());
            runJob.setJobId(job.getId());
            runJob.setStageName(stage == null ? "" : stage.getName());
            runJob.setJobName(job.getName());
            runJob.setKind(job.getKind());
            runJob.setCommand(command);
            runJob.setStatus(clusterReady ? "QUEUED" : "FAILED");
            runJob.setLogText(clusterReady ? null : error);
            runJobMapper.insert(runJob);
        }
        if (clusterReady) {
            Long runId = run.getId();
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
        return get(pipelineId, run.getId());
    }

    public PipelineRunVO get(Long pipelineId, Long runId) {
        definitionService.requireOwned(pipelineId);
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null || !pipelineId.equals(run.getPipelineId())) {
            throw BizException.of(ResultCode.NOT_FOUND, "运行记录不存在");
        }
        PipelineRunVO vo = new PipelineRunVO();
        vo.setId(run.getId());
        vo.setPipelineId(run.getPipelineId());
        vo.setPipelineName(definitionService.requireOwned(pipelineId).getName());
        vo.setStatus(run.getStatus());
        vo.setTrigger(run.getTrigger());
        vo.setGitRef(run.getGitRef());
        vo.setCommitSha(run.getCommitSha());
        vo.setStack(run.getStack());
        vo.setRuntimeVersion(run.getRuntimeVersion());
        vo.setToolVersion(run.getToolVersion());
        vo.setImage(run.getImage());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setJobs(runJobMapper.selectList(new LambdaQueryWrapper<PipelineRunJobEntity>()
                        .eq(PipelineRunJobEntity::getRunId, runId)
                        .orderByAsc(PipelineRunJobEntity::getId))
                .stream()
                .map(this::toJobVo)
                .toList());
        return vo;
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
        vo.setStartedAt(row.getStartedAt());
        vo.setFinishedAt(row.getFinishedAt());
        return vo;
    }
}
