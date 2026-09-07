package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.entity.PipelineEntity;
import com.hfwas.devops.pipeline.entity.PipelineJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.entity.PipelineStageEntity;
import com.hfwas.devops.pipeline.executor.UnavailablePipelineExecutor;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
}
