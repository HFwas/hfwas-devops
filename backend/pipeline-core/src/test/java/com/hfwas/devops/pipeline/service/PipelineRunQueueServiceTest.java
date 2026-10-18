package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineJobParamMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIPE-041..046：取消、暂停、并发上限与队列出队。
 */
@ExtendWith(MockitoExtension.class)
class PipelineRunQueueServiceTest {

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
    private PipelineJobParamMapper jobParamMapper;
    @Mock
    private PipelineTaskKindParamMapper taskKindParamMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private PipelineExecutor pipelineExecutor;


    private PipelineRunService service;

    @BeforeEach
    void setUp() {
        service = new PipelineRunService(definitionService, runMapper, runJobMapper, stageMapper, jobMapper,
                jobParamMapper, taskKindParamMapper, currentUserAccessor, pipelineExecutor, 2);
        lenient().when(currentUserAccessor.currentTenantId()).thenReturn(9L);
        lenient().when(currentUserAccessor.currentUserId()).thenReturn(7L);
        lenient().when(definitionService.requireOwned(1L)).thenAnswer(inv -> pipeline());
        lenient().when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    @DisplayName("PIPE-041: 运行中取消置为 CANCELLED 并调用执行器")
    void cancelRunningMarksCancelledAndCallsExecutor() {
        PipelineRunEntity run = run("RUNNING");
        when(runMapper.selectById(99L)).thenReturn(run);

        PipelineRunVO vo = service.cancel(1L, 99L);

        assertThat(vo.getStatus()).isEqualTo("CANCELLED");
        assertThat(run.getFinishedAt()).isNotNull();
        verify(runMapper).updateById(run);
        verify(pipelineExecutor).cancel(99L);
    }

    @Test
    @DisplayName("PIPE-042: 终态取消原样返回且不调用执行器")
    void cancelTerminalIsNoop() {
        PipelineRunEntity run = run("SUCCEEDED");
        when(runMapper.selectById(99L)).thenReturn(run);

        PipelineRunVO vo = service.cancel(1L, 99L);

        assertThat(vo.getStatus()).isEqualTo("SUCCEEDED");
        verify(pipelineExecutor, never()).cancel(99L);
        verify(runMapper, never()).updateById(any(PipelineRunEntity.class));
    }

    @Test
    @DisplayName("PIPE-041: 执行器取消失败不影响接口返回")
    void cancelSwallowsExecutorFailure() {
        PipelineRunEntity run = run("QUEUED");
        when(runMapper.selectById(99L)).thenReturn(run);
        doThrow(new RuntimeException("cluster down")).when(pipelineExecutor).cancel(99L);

        PipelineRunVO vo = service.cancel(1L, 99L);

        assertThat(vo.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("PIPE-048: 取消不存在/他人运行返回 10003")
    void cancelRejectsMissingRun() {
        when(runMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.cancel(1L, 99L));

        assertThat(ex.getMessage()).isEqualTo("运行记录不存在");
    }

    @Test
    @DisplayName("PIPE-044: 租户并发达到上限时新运行保持 QUEUED")
    void startKeepsQueuedWhenConcurrencyLimitReached() {
        stubStartGraph();
        when(pipelineExecutor.isReady()).thenReturn(true);
        when(runMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        PipelineRunVO vo = service.start(1L, Map.<String, String>of());

        assertThat(vo.getStatus()).isEqualTo("QUEUED");
        verify(pipelineExecutor, never()).submit(99L);
    }

    @Test
    @DisplayName("PIPE-045: 出队按 id 取最早的 QUEUED 并提交")
    void dequeueSubmitsOldestQueuedRun() {
        PipelineRunEntity next = run("QUEUED");
        when(runMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(next);
        when(definitionService.loadGraph(1L)).thenReturn(graph());

        service.dequeueNextRun(9L);

        assertThat(next.getSegmentIndex()).isZero();
        verify(runMapper).updateById(next);
        verify(pipelineExecutor).submit(99L);
    }

    @Test
    @DisplayName("PIPE-045: 队列为空时不提交")
    void dequeueWithoutQueuedRunIsNoop() {
        when(runMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.dequeueNextRun(9L);

        verify(pipelineExecutor, never()).submit(any());
    }

    @Test
    @DisplayName("PIPE-045: 队列中的流水线图已为空时不提交")
    void dequeueSkipsEmptyGraph() {
        PipelineRunEntity next = run("QUEUED");
        when(runMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(next);
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of()));

        service.dequeueNextRun(9L);

        verify(pipelineExecutor, never()).submit(any());
    }

    @Test
    @DisplayName("PIPE-904: 审批续跑也应受并发上限约束（当前实现绕过）")
    @Disabled("PIPE-904: 审批续跑当前直接 submit，未检查 pipeline.max-concurrent-runs")
    void approveShouldRespectConcurrencyLimit() {
        PipelineRunEntity run = run("WAITING_APPROVAL");
        when(runMapper.selectById(99L)).thenReturn(run);
        PipelineRunJobEntity gate = new PipelineRunJobEntity();
        gate.setId(1L);
        gate.setRunId(99L);
        gate.setKind("APPROVAL");
        gate.setStatus("WAITING_APPROVAL");
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gate));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "gate", 0, List.of(
                        new PipelineJobSpec(11L, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0))),
                new PipelineStageSpec(20L, "run", 1, List.of(
                        new PipelineJobSpec(21L, "echo", PipelineJobKind.CUSTOM, "echo ok", null, null, null, 0)))
        )));
        when(runMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        service.approve(1L, 99L);

        verify(pipelineExecutor, never()).submit(99L);
    }

    @Test
    @DisplayName("PIPE-903: 取消应同步把非终态任务置为 CANCELLED")
    @Disabled("PIPE-903: 当前取消只更新 pipeline_run，不更新 pipeline_run_job")
    void cancelShouldMarkJobsCancelled() {
        PipelineRunEntity run = run("RUNNING");
        when(runMapper.selectById(99L)).thenReturn(run);
        PipelineRunJobEntity job = new PipelineRunJobEntity();
        job.setId(1L);
        job.setRunId(99L);
        job.setStatus("RUNNING");
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));

        service.cancel(1L, 99L);

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
    }

    private void stubStartGraph() {
        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setName("build");
        stage.setSortOrder(0);
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));
        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setStageId(11L);
        job.setName("build");
        job.setKind("BUILD");
        job.setCommand("make");
        job.setSortOrder(0);
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        when(definitionService.loadGraph(1L)).thenReturn(graph());
        AtomicReference<PipelineRunEntity> stored = new AtomicReference<>();
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(inv -> {
            PipelineRunEntity entity = inv.getArgument(0);
            entity.setId(99L);
            stored.set(entity);
            return 1;
        });
        when(runMapper.selectById(99L)).thenAnswer(inv -> stored.get());
    }

    private static PipelineGraphSpec graph() {
        return new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "build", 0, List.of(
                        new PipelineJobSpec(21L, "build", PipelineJobKind.BUILD, "make", null, null, null, 0)))
        ));
    }

    private static PipelineEntity pipeline() {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
        pipeline.setGitRef("main");
        return pipeline;
    }

    private static PipelineRunEntity run(String status) {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(99L);
        run.setPipelineId(1L);
        run.setTenantId(9L);
        run.setStatus(status);
        return run;
    }
}
