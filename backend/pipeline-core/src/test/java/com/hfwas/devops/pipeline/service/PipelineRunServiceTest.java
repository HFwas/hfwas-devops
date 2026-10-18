package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.dto.RunParamDefinitionVO;
import com.hfwas.devops.pipeline.entity.PipelineTaskKindParamEntity;
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
import com.hfwas.devops.pipeline.mapper.PipelineJobParamMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindParamMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private PipelineJobParamMapper jobParamMapper;
    @Mock
    private PipelineTaskKindParamMapper taskKindParamMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;
    @Mock
    private PipelineExecutor pipelineExecutor;

    private final AtomicReference<PipelineRunEntity> stored = new AtomicReference<>();

    @Test
    void startWithoutClusterFailsWithReadableMessage() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper, taskKindParamMapper, currentUserAccessor,
                new UnavailablePipelineExecutor(), 10);

        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
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
                        new PipelineJobSpec(21L, "clone", PipelineJobKind.CLONE, "", null, null, null, 0)
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
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper, taskKindParamMapper, currentUserAccessor,
                pipelineExecutor, 10);
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
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper, taskKindParamMapper, currentUserAccessor,
                pipelineExecutor, 10);
        stubOwned();
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "gate", 0, List.of(
                        new PipelineJobSpec(11L, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0))),
                new PipelineStageSpec(20L, "run", 1, List.of(
                        new PipelineJobSpec(21L, "echo", PipelineJobKind.CUSTOM, "echo ok", null, null, null, 0)))
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
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper, taskKindParamMapper, currentUserAccessor,
                pipelineExecutor, 10);
        stubOwned();
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "a1", 0, List.of(
                        new PipelineJobSpec(11L, "ok1", PipelineJobKind.APPROVAL, "", null, null, null, 0))),
                new PipelineStageSpec(11L, "a2", 1, List.of(
                        new PipelineJobSpec(12L, "ok2", PipelineJobKind.APPROVAL, "", null, null, null, 0))),
                new PipelineStageSpec(20L, "run", 2, List.of(
                        new PipelineJobSpec(21L, "echo", PipelineJobKind.CUSTOM, "echo ok", null, null, null, 0)))
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

    @Test
    void hiddenParamsStayOutOfRunDialogAndKeepMarketplaceDefault() {
        PipelineRunService service = new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper, taskKindParamMapper, currentUserAccessor,
                new UnavailablePipelineExecutor(), 10);
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
        pipeline.setGitRef("main");
        when(definitionService.requireOwned(1L)).thenReturn(pipeline);
        when(currentUserAccessor.currentUserId()).thenReturn(7L);

        PipelineTaskKindParamEntity hidden = param("TOKEN", "hidden", "secret");
        PipelineTaskKindParamEntity visible = param("PROFILE", "user", "dev");
        when(taskKindParamMapper.selectAll()).thenReturn(List.of(hidden, visible));

        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(11L);
        stage.setName("build");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));

        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(21L);
        job.setStageId(11L);
        job.setName("build");
        job.setKind("BUILD");
        job.setSortOrder(0);
        job.setParamBindings("{\"PROFILE\":{\"mode\":\"runtime\"},\"TOKEN\":{\"mode\":\"runtime\"}}");
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "build", 0, List.of(
                        new PipelineJobSpec(21L, "build", PipelineJobKind.BUILD, "mvn package", null, null, null, 0)
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

        List<RunParamDefinitionVO> dialog = service.getDefaultParams(1L);
        assertEquals(List.of("PROFILE"), dialog.stream().map(RunParamDefinitionVO::getParamKey).toList());

        service.start(1L, Map.of("TOKEN", "from-user", "PROFILE", "release"));

        assertEquals(true, stored.get().getRuntimeParams().contains("\"TOKEN\":\"secret\""));
        assertEquals(true, stored.get().getRuntimeParams().contains("\"PROFILE\":\"release\""));
    }

    @Test
    void startQueuesAndSubmitsWhenClusterReady() {
        PipelineRunService service = service(10);
        stubStartGraph(job("build", "BUILD", "mvn package"));
        when(pipelineExecutor.isReady()).thenReturn(true);
        when(currentUserAccessor.currentDisplayName()).thenReturn("ada");
        when(currentUserAccessor.currentUserId()).thenReturn(7L);
        when(runMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        PipelineRunVO vo = service.start(1L, Map.<String, String>of());

        assertEquals("QUEUED", vo.getStatus());
        assertEquals("MANUAL", vo.getTrigger());
        assertEquals("ada", vo.getTriggeredByName());
        assertEquals("main", vo.getGitRef());
        assertEquals(0, stored.get().getSegmentIndex());
        verify(pipelineExecutor).submit(99L);
    }

    @Test
    void startWaitsWhenFirstStageIsApproval() {
        PipelineRunService service = service(10);
        stubPipeline();
        PipelineStageEntity stage = stage(11L, "gate");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));
        PipelineJobEntity approval = jobEntity(21L, 11L, "ok", "APPROVAL", "");
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(approval));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "gate", 0, List.of(
                        new PipelineJobSpec(21L, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0)))
        )));
        stubRunInsert();
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(pipelineExecutor.isReady()).thenReturn(true);

        PipelineRunVO vo = service.start(1L, Map.<String, String>of());

        assertEquals("WAITING_APPROVAL", vo.getStatus());
        ArgumentCaptor<PipelineRunJobEntity> captor = ArgumentCaptor.forClass(PipelineRunJobEntity.class);
        verify(runJobMapper).insert(captor.capture());
        assertEquals("WAITING_APPROVAL", captor.getValue().getStatus());
        verify(pipelineExecutor, never()).submit(99L);
    }

    @Test
    void startRejectsMissingRequiredRuntimeParam() {
        PipelineRunService service = service(10);
        stubRuntimeParam(param("PROFILE", "user", "dev"), true);

        BizException ex = assertThrows(BizException.class, () -> service.start(1L, Map.of()));

        assertEquals("运行时参数 [PROFILE] 为必填项", ex.getMessage());
        verify(runMapper, never()).insert(any(PipelineRunEntity.class));
    }

    @Test
    void startRejectsSelectValueOutsideOptions() {
        PipelineRunService service = service(10);
        PipelineTaskKindParamEntity param = param("PROFILE", "user", "dev");
        param.setParamType("select");
        param.setOptionsJson("[\"dev\",\"prod\"]");
        stubRuntimeParam(param, false);

        BizException ex = assertThrows(BizException.class, () -> service.start(1L, Map.of("PROFILE", "test")));

        assertEquals("参数 [PROFILE] 的值不在可选范围内", ex.getMessage());
    }

    @Test
    void startUsesGitRefOverrideAndReplacesCmdPlaceholder() {
        PipelineRunService service = service(10);
        stubStartGraph(job("build", "BUILD", "echo ${CMD_VERSION}"));
        when(pipelineExecutor.isReady()).thenReturn(true);
        when(currentUserAccessor.currentUserId()).thenReturn(7L);
        when(runMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.start(1L, Map.of("GIT_REF", "feature/x", "CMD_VERSION", "1.2.3"));

        assertEquals("feature/x", stored.get().getGitRef());
        ArgumentCaptor<PipelineRunJobEntity> captor = ArgumentCaptor.forClass(PipelineRunJobEntity.class);
        verify(runJobMapper).insert(captor.capture());
        assertEquals("echo 1.2.3", captor.getValue().getCommand());
    }

    @Test
    void getRejectsRunFromAnotherPipeline() {
        PipelineRunService service = service(10);
        stubOwned();
        PipelineRunEntity run = storedRun("SUCCEEDED");
        run.setPipelineId(2L);
        when(runMapper.selectById(99L)).thenReturn(run);

        BizException ex = assertThrows(BizException.class, () -> service.get(1L, 99L));

        assertEquals("运行记录不存在", ex.getMessage());
    }

    @Test
    void pageRunsReturnsRowsWithoutJobs() {
        PipelineRunService service = service(10);
        stubOwned();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PipelineRunEntity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(List.of(storedRun("SUCCEEDED")));
        when(runMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.pageRuns(1L, new com.hfwas.devops.pipeline.dto.PipelinePageQuery());

        assertEquals(1, result.getRecords().size());
        assertEquals(0, result.getRecords().getFirst().getJobs().size());
    }

    @Test
    void approveLastGateCompletesRun() {
        PipelineRunService service = service(10);
        stubOwned();
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(10L, "gate", 0, List.of(
                        new PipelineJobSpec(11L, "ok", PipelineJobKind.APPROVAL, "", null, null, null, 0)))
        )));
        PipelineRunEntity run = storedRun("WAITING_APPROVAL");
        when(runMapper.selectById(99L)).thenReturn(run);
        List<PipelineRunJobEntity> jobs = new ArrayList<>();
        jobs.add(runJob(1L, 11L, "ok", "APPROVAL", "WAITING_APPROVAL"));
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> List.copyOf(jobs));

        PipelineRunVO vo = service.approve(1L, 99L);

        assertEquals("SUCCEEDED", vo.getStatus());
        assertNotNull(vo.getFinishedAt());
        verify(pipelineExecutor, never()).submit(99L);
    }

    @Test
    @org.junit.jupiter.api.Disabled("PIPE-915: fixed 绑定应禁止被运行时参数覆盖")
    void fixedParamsShouldNotBeOverriddenByRuntime() {
        PipelineRunService service = service(10);
        stubPipeline();
        PipelineStageEntity stage = stage(11L, "build");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));
        PipelineJobEntity job = jobEntity(21L, 11L, "build", "BUILD", "mvn package");
        job.setParamBindings("{\"VERSION\":{\"mode\":\"fixed\",\"value\":\"1.0\"}}");
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        PipelineTaskKindParamEntity version = param("VERSION", "user", "1.0");
        when(taskKindParamMapper.selectAll()).thenReturn(List.of(version));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "build", 0, List.of(
                        new PipelineJobSpec(21L, "build", PipelineJobKind.BUILD, "mvn package", null, null, null, 0)))
        )));
        stubRunInsert();
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(pipelineExecutor.isReady()).thenReturn(false);

        service.start(1L, Map.of("VERSION", "2.0"));

        assertEquals(true, stored.get().getRuntimeParams().contains("\"VERSION\":\"1.0\""));
    }

    @Test
    void approveWhenRunNotFoundThrows() {
        PipelineRunService service = service(10);
        stubOwned();
        when(runMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.approve(1L, 99L));

        assertEquals("运行记录不存在", ex.getMessage());
    }

    private PipelineRunService service(int maxConcurrentRuns) {
        return new PipelineRunService(
                definitionService, runMapper, runJobMapper, stageMapper, jobMapper, jobParamMapper,
                taskKindParamMapper, currentUserAccessor, pipelineExecutor, maxConcurrentRuns);
    }

    private void stubPipeline() {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
        pipeline.setGitRef("main");
        when(definitionService.requireOwned(1L)).thenReturn(pipeline);
    }

    private void stubStartGraph(PipelineJobEntity job) {
        stubPipeline();
        PipelineStageEntity stage = stage(11L, "build");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        when(definitionService.loadGraph(1L)).thenReturn(new PipelineGraphSpec(List.of(
                new PipelineStageSpec(11L, "build", 0, List.of(
                        new PipelineJobSpec(job.getId(), job.getName(), PipelineJobKind.valueOf(job.getKind()),
                                job.getCommand(), null, null, null, 0)))
        )));
        stubRunInsert();
        when(runJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    private void stubRuntimeParam(PipelineTaskKindParamEntity param, boolean required) {
        stubPipeline();
        PipelineStageEntity stage = stage(11L, "build");
        when(stageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stage));
        PipelineJobEntity job = jobEntity(21L, 11L, "build", "BUILD", "mvn package");
        job.setParamBindings("{\"PROFILE\":{\"mode\":\"runtime\"}}");
        when(jobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        param.setRequired(required ? 1 : 0);
        when(taskKindParamMapper.selectAll()).thenReturn(List.of(param));
    }

    private void stubRunInsert() {
        when(runMapper.insert(any(PipelineRunEntity.class))).thenAnswer(invocation -> {
            PipelineRunEntity run = invocation.getArgument(0);
            run.setId(99L);
            stored.set(run);
            return 1;
        });
        when(runMapper.selectById(99L)).thenAnswer(invocation -> stored.get());
    }

    private static PipelineStageEntity stage(Long id, String name) {
        PipelineStageEntity stage = new PipelineStageEntity();
        stage.setId(id);
        stage.setName(name);
        stage.setSortOrder(0);
        return stage;
    }

    private static PipelineJobEntity jobEntity(Long id, Long stageId, String name, String kind, String command) {
        PipelineJobEntity job = new PipelineJobEntity();
        job.setId(id);
        job.setStageId(stageId);
        job.setName(name);
        job.setKind(kind);
        job.setCommand(command);
        job.setSortOrder(0);
        return job;
    }

    private PipelineJobEntity job(String name, String kind, String command) {
        return jobEntity(21L, 11L, name, kind, command);
    }

    private static PipelineTaskKindParamEntity param(String key, String visibility, String defaultValue) {
        PipelineTaskKindParamEntity entity = new PipelineTaskKindParamEntity();
        entity.setKindValue("BUILD");
        entity.setParamKey(key);
        entity.setParamLabel(key);
        entity.setParamType("input");
        entity.setVisibility(visibility);
        entity.setDefaultValue(defaultValue);
        entity.setRequired(0);
        return entity;
    }

    private void stubOwned() {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(1L);
        pipeline.setTenantId(9L);
        pipeline.setName("demo");
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
