package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.pipeline.dto.DependencyScanSubmitDTO;
import com.hfwas.devops.pipeline.dto.DependencyScanVO;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.mapper.PipelineRunArtifactMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyScanServiceTest {

    @Mock
    private PipelineDefinitionService definitionService;
    @Mock
    private PipelineRunService runService;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private PipelineRunArtifactMapper artifactMapper;
    @Mock
    private CurrentUserAccessor currentUserAccessor;

    @Captor
    private ArgumentCaptor<com.hfwas.devops.pipeline.dto.PipelineSaveDTO> pipelineCaptor;

    private DependencyScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new DependencyScanService(definitionService, runService, runMapper, artifactMapper, currentUserAccessor);
    }

    @Test
    void submitSingleCreatesPipelineAndStartsRun() {
        // 模拟保存流水线返回 ID
        when(definitionService.save(any())).thenReturn(42L);
        // 模拟启动运行返回 VO
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(100L);
        runVo.setPipelineId(42L);
        runVo.setStatus("QUEUED");
        when(runService.start(42L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/my-app.git");
        dto.setGitRef("main");

        DependencyScanVO result = scanService.submitSingle(dto);

        assertNotNull(result);
        assertEquals(42L, result.getPipelineId());
        assertEquals(100L, result.getRunId());
        assertEquals("QUEUED", result.getStatus());
        assertTrue(result.getPipelineName().contains("my-app"));

        // 验证创建的流水线包含 CLONE + BUILD + DEPENDENCY_ANALYSIS
        verify(definitionService).save(pipelineCaptor.capture());
        var pipelineDto = pipelineCaptor.getValue();
        assertEquals("[依赖扫描] my-app", pipelineDto.getName());
        assertEquals("https://github.com/org/my-app.git", pipelineDto.getRepoUrl());
        assertEquals(3, pipelineDto.getStages().size());
        assertEquals("代码克隆", pipelineDto.getStages().get(0).getName());
        assertEquals("安装依赖", pipelineDto.getStages().get(1).getName());
        assertEquals("依赖分析", pipelineDto.getStages().get(2).getName());
        assertEquals("CLONE", pipelineDto.getStages().get(0).getJobs().getFirst().getKind());
        assertEquals("BUILD", pipelineDto.getStages().get(1).getJobs().getFirst().getKind());
        assertEquals("DEPENDENCY_ANALYSIS", pipelineDto.getStages().get(2).getJobs().getFirst().getKind());
    }

    @Test
    void submitSingleWithJavaStackUsesMavenPlugin() {
        when(definitionService.save(any())).thenReturn(43L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(101L);
        runVo.setPipelineId(43L);
        runVo.setStatus("QUEUED");
        when(runService.start(43L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/java-app.git");
        dto.setStack("JAVA_MAVEN");

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var analysisJob = pipelineCaptor.getValue().getStages().get(2).getJobs().getFirst();
        assertEquals("DEPENDENCY_ANALYSIS", analysisJob.getKind());
        assertTrue(analysisJob.getCommand().contains("cyclonedx-maven-plugin"),
                "Java projects should use Maven plugin, got: " + analysisJob.getCommand());
    }

    @Test
    void submitSingleWithNodeStackUsesCdxgen() {
        when(definitionService.save(any())).thenReturn(44L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(102L);
        runVo.setPipelineId(44L);
        runVo.setStatus("QUEUED");
        when(runService.start(44L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/node-app.git");
        dto.setStack("NODE");

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var analysisJob = pipelineCaptor.getValue().getStages().get(2).getJobs().getFirst();
        assertEquals("DEPENDENCY_ANALYSIS", analysisJob.getKind());
        assertTrue(analysisJob.getCommand().contains("cdxgen"),
                "Node projects should use cdxgen, got: " + analysisJob.getCommand());
    }

    @Test
    void submitSingleWithGoStackUsesGoBuildCommand() {
        when(definitionService.save(any())).thenReturn(45L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(103L);
        runVo.setPipelineId(45L);
        runVo.setStatus("QUEUED");
        when(runService.start(45L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/go-app.git");
        dto.setStack("GO");

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var buildJob = pipelineCaptor.getValue().getStages().get(1).getJobs().getFirst();
        assertEquals("BUILD", buildJob.getKind());
        assertTrue(buildJob.getCommand().contains("go mod download"),
                "Go projects should use go mod download, got: " + buildJob.getCommand());
    }

    @Test
    void extractRepoNameFromUrl() {
        assertEquals("my-app",
                scanService.submitSingle(createDto("https://github.com/org/my-app.git")).getPipelineName()
                        .replace("[依赖扫描] ", ""));
    }

    private static DependencyScanSubmitDTO createDto(String repoUrl) {
        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl(repoUrl);
        dto.setGitRef("main");
        return dto;
    }
}