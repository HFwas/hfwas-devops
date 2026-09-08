package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.executor.UnavailablePipelineExecutor;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineRunServiceTest {

    @Mock
    private PipelineDefinitionService definitionService;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private PipelineRunJobMapper runJobMapper;
    @Mock
    private PipelineStageMapper stageMapper;
    @Mock
    private PipelineJobMapper jobMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private PipelineExecutor pipelineExecutor;

    @Test
    void startWithoutClusterFailsWithReadableMessage() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, currentUserAccessor,
                new UnavailablePipelineExecutor());

        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
        pipeline.setStack("JAVA_MAVEN");
        pipeline.setRuntimeVersion("21");
        pipeline.setToolVersion("3.9");
        pipeline.setGitRef("main");
        when(definitionService.requireOwned(1L)).thenReturn(pipeline);
        when(currentUserAccessor.currentUserId()).thenReturn(7L);

        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setName("clone");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));

        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setStageId(11L);
        job.setName("clone");
        job.setKind("CLONE");
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "clone", 0, List.of(
                        new PipelineJobSpec(21L, "clone", PipelineJobKind.CLONE, "", 0)
                ))
        )));

        AtomicReference<PipelineRunEntity> stored = new AtomicReference<>();
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(99L);
            stored.set(run);
            return 1;
        });
        when(runMapper.selectById(99L)).thenAnswer(invocation -> stored.get());
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PipelineRunVO vo = service.start(1L);

        assertEquals("FAILED", vo.getStatus());
        assertEquals("maven:3.9.9-eclipse-temurin-21", vo.getImage());
        assertNotNull(vo.getErrorMessage());
        assertEquals(true, vo.getErrorMessage().contains("未配置执行集群"));

        ArgumentCaptor<PipelineRunJobEntity> jobCaptor = ArgumentCaptor.forClass(PipelineRunJobEntity.class);
        verify(runJobMapper).insert(jobCaptor.capture());
        assertEquals("FAILED", jobCaptor.getValue().getStatus());
        assertEquals("git clone", jobCaptor.getValue().getCommand());
    }

    @Test
    void approveWhenNotWaitingRejected() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, currentUserAccessor,
                pipelineExecutor);
        stubOwned();
        PipelineRunEntity run = storedRun("RUNNING");
        when(runMapper.selectById(99L)).thenReturn(run);
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.approve(1L, 99L));
        assertEquals("当前运行不在待审批", ex.getMessage());
        verify(pipelineExecutor, never()).submit(99L);
    }

    @Test
    void approveSubmitsSegmentAfterLeadingGate() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, currentUserAccessor,
                pipelineExecutor);
        stubOwned();
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "gate", 0, List.of(
                        new PipelineJobSpec(11L, "ok", PipelineJobKind.APPROVAL, "", 0))),
                new PipelineStageSpec(20L, "run", 1, List.of(
                        new PipelineJobSpec(21L, "echo", PipelineJobKind.CUSTOM, "echo ok", 0)))
        )));
        PipelineRunEntity run = storedRun("WAITING_APPROVAL");
        when(runMapper.selectById(99L)).thenReturn(run);
        List<PipelineRunJobEntity> jobs = new ArrayList<>();
        jobs.add(runJob(1L, 11L, "ok", "APPROVAL", "WAITING_APPROVAL"));
        jobs.add(runJob(2L, 21L, "echo", "CUSTOM", "QUEUED"));
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> List.copyOf(jobs));

        PipelineRunVO vo = service.approve(1L, 99L);

        assertEquals("QUEUED", vo.getStatus());
        assertEquals(0, run.getSegmentIndex());
        assertEquals("SUCCEEDED", jobs.get(0).getStatus());
        verify(pipelineExecutor).submit(99L);
    }

    @Test
    void approveConsecutiveGatesStayWaiting() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, currentUserAccessor,
                pipelineExecutor);
        stubOwned();
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "a1", 0, List.of(
                        new PipelineJobSpec(11L, "ok1", PipelineJobKind.APPROVAL, "", 0))),
                new PipelineStageSpec(11L, "a2", 1, List.of(
                        new PipelineJobSpec(12L, "ok2", PipelineJobKind.APPROVAL, "", 0))),
                new PipelineStageSpec(20L, "run", 2, List.of(
                        new PipelineJobSpec(21L, "echo", PipelineJobKind.CUSTOM, "echo ok", 0)))
        )));
        PipelineRunEntity run = storedRun("WAITING_APPROVAL");
        when(runMapper.selectById(99L)).thenReturn(run);
        List<PipelineRunJobEntity> jobs = new ArrayList<>();
        jobs.add(runJob(1L, 11L, "ok1", "APPROVAL", "WAITING_APPROVAL"));
        jobs.add(runJob(2L, 12L, "ok2", "APPROVAL", "QUEUED"));
        jobs.add(runJob(3L, 21L, "echo", "CUSTOM", "QUEUED"));
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> List.copyOf(jobs));

        PipelineRunVO vo = service.approve(1L, 99L);

        assertEquals("WAITING_APPROVAL", vo.getStatus());
        assertEquals("SUCCEEDED", jobs.get(0).getStatus());
        assertEquals("WAITING_APPROVAL", jobs.get(1).getStatus());
        verify(pipelineExecutor, never()).submit(99L);
    }

    private void stubOwned() {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
        pipeline.setStack("JAVA_MAVEN");
        pipeline.setRuntimeVersion("21");
        pipeline.setToolVersion("3.9");
        when(definitionService.requireOwned(1L)).thenReturn(pipeline);
    }

    private static PipelineRunEntity storedRun(String status) {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(99L);
        run.setPipelineId(1L);
        run.setStatus(status);
        return run;
    }

    private static PipelineRunJobEntity runJob(Long id, Long jobId, String name, String kind, String status) {
        PipelineRunJobEntity job = new PipelineRunJobEntity();
        job.setId(id);
        job.setRunId(99L);
        job.setJobId(jobId);
        job.setJobName(name);
        job.setKind(kind);
        job.setStatus(status);
        return job;
    }
}
