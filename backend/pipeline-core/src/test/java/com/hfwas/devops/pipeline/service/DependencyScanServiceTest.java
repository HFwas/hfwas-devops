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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(definitionService.save(any())).thenReturn(42L);
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

    // =========== 新测试：模块选择 ===========

    @Test
    void submitSingleWithModulePathPrefixesCommand() {
        when(definitionService.save(any())).thenReturn(46L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(104L);
        runVo.setPipelineId(46L);
        runVo.setStatus("QUEUED");
        when(runService.start(46L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/monorepo.git");
        dto.setModulePath("backend");
        dto.setStack("JAVA_MAVEN");

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var pipelineDto = pipelineCaptor.getValue();
        var buildJob = pipelineDto.getStages().get(1).getJobs().getFirst();
        var analysisJob = pipelineDto.getStages().get(2).getJobs().getFirst();

        // BUILD 命令应 cd 到模块路径
        assertTrue(buildJob.getCommand().startsWith("cd backend && "),
                "BUILD command should cd to module, got: " + buildJob.getCommand());
        assertTrue(buildJob.getCommand().contains("mvn dependency:resolve"),
                "BUILD command should use Maven, got: " + buildJob.getCommand());

        // DEPENDENCY_ANALYSIS 命令也应 cd 到模块路径
        assertTrue(analysisJob.getCommand().startsWith("cd backend && "),
                "Analysis command should cd to module, got: " + analysisJob.getCommand());
        assertTrue(analysisJob.getCommand().contains("cyclonedx-maven-plugin"),
                "Analysis command should use Maven plugin, got: " + analysisJob.getCommand());
    }

    @Test
    void submitSingleWithNodeModulePath() {
        when(definitionService.save(any())).thenReturn(47L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(105L);
        runVo.setPipelineId(47L);
        runVo.setStatus("QUEUED");
        when(runService.start(47L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/monorepo.git");
        dto.setModulePath("frontend");
        dto.setStack("NODE");

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var pipelineDto = pipelineCaptor.getValue();
        var buildJob = pipelineDto.getStages().get(1).getJobs().getFirst();
        var analysisJob = pipelineDto.getStages().get(2).getJobs().getFirst();

        assertTrue(buildJob.getCommand().startsWith("cd frontend && "),
                "BUILD command should cd to module, got: " + buildJob.getCommand());
        assertTrue(buildJob.getCommand().contains("npm ci"),
                "BUILD command should use npm, got: " + buildJob.getCommand());

        assertTrue(analysisJob.getCommand().startsWith("cd frontend && "),
                "Analysis command should cd to module, got: " + analysisJob.getCommand());
        assertTrue(analysisJob.getCommand().contains("cdxgen"),
                "Analysis command should use cdxgen, got: " + analysisJob.getCommand());
    }

    @Test
    void submitSingleWithModulePathAutoDetectWhenStackNull() {
        when(definitionService.save(any())).thenReturn(48L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(106L);
        runVo.setPipelineId(48L);
        runVo.setStatus("QUEUED");
        when(runService.start(48L)).thenReturn(runVo);

        DependencyScanSubmitDTO dto = new DependencyScanSubmitDTO();
        dto.setRepoUrl("https://github.com/org/monorepo.git");
        dto.setModulePath("frontend");
        // stack 为空，走自动检测

        scanService.submitSingle(dto);

        verify(definitionService).save(pipelineCaptor.capture());
        var pipelineDto = pipelineCaptor.getValue();
        var buildJob = pipelineDto.getStages().get(1).getJobs().getFirst();
        var analysisJob = pipelineDto.getStages().get(2).getJobs().getFirst();

        // BUILD 命令：cd 到模块 + 运行时自动检测
        assertTrue(buildJob.getCommand().startsWith("cd frontend && "));
        assertTrue(buildJob.getCommand().contains("if [ -f pom.xml ]"),
                "Auto-detect BUILD should check for pom.xml, got: " + buildJob.getCommand());

        // DEPENDENCY_ANALYSIS 命令：cd 到模块 + 运行时自动检测
        assertTrue(analysisJob.getCommand().startsWith("cd frontend && "));
        assertTrue(analysisJob.getCommand().contains("if [ -f pom.xml ]"),
                "Auto-detect analysis should check for pom.xml, got: " + analysisJob.getCommand());
        assertTrue(analysisJob.getCommand().contains("if [ -f package.json ]"),
                "Auto-detect analysis should check for package.json, got: " + analysisJob.getCommand());
    }

    @Test
    void extractRepoNameFromUrl() {
        when(definitionService.save(any())).thenReturn(42L);
        PipelineRunVO runVo = new PipelineRunVO();
        runVo.setId(100L);
        runVo.setPipelineId(42L);
        runVo.setStatus("QUEUED");
        when(runService.start(anyLong())).thenReturn(runVo);

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

    // =========== 命令生成单元测试 ===========

    @Test
    void buildCommandWithJavaStackNoModule() {
        String cmd = DependencyScanService.buildCommand("JAVA_MAVEN", null);
        assertEquals("mvn dependency:resolve -q --no-transfer-progress", cmd);
    }

    @Test
    void buildCommandWithNodeStackNoModule() {
        String cmd = DependencyScanService.buildCommand("NODE", null);
        assertEquals("npm ci || npm install", cmd);
    }

    @Test
    void buildCommandWithJavaStackAndModule() {
        String cmd = DependencyScanService.buildCommand("JAVA_MAVEN", "backend");
        assertEquals("cd backend && mvn dependency:resolve -q --no-transfer-progress", cmd);
    }

    @Test
    void buildCommandWithNodeStackAndModule() {
        String cmd = DependencyScanService.buildCommand("NODE", "frontend");
        assertEquals("cd frontend && npm ci || npm install", cmd);
    }

    @Test
    void buildCommandWithNullStackAndModule() {
        String cmd = DependencyScanService.buildCommand(null, "backend");
        assertTrue(cmd.startsWith("cd backend && "));
        assertTrue(cmd.contains("if [ -f pom.xml ]"));
        assertTrue(cmd.contains("mvn dependency:resolve"));
    }

    @Test
    void buildCommandWithNullStackAndNullModule() {
        String cmd = DependencyScanService.buildCommand(null, null);
        assertEquals("echo 'no build needed'", cmd);
    }

    @Test
    void dependencyAnalysisCommandWithJavaStackNoModule() {
        String cmd = DependencyScanService.dependencyAnalysisCommand("JAVA_MAVEN", null);
        assertTrue(cmd.contains("cyclonedx-maven-plugin"));
    }

    @Test
    void dependencyAnalysisCommandWithNodeStackNoModule() {
        String cmd = DependencyScanService.dependencyAnalysisCommand("NODE", null);
        assertTrue(cmd.contains("cdxgen"));
    }

    @Test
    void dependencyAnalysisCommandWithJavaStackAndModule() {
        String cmd = DependencyScanService.dependencyAnalysisCommand("JAVA_MAVEN", "backend");
        assertTrue(cmd.startsWith("cd backend && "));
        assertTrue(cmd.contains("cyclonedx-maven-plugin"));
    }

    @Test
    void dependencyAnalysisCommandWithNullStackAndModule() {
        String cmd = DependencyScanService.dependencyAnalysisCommand(null, "frontend");
        assertTrue(cmd.startsWith("cd frontend && "));
        assertTrue(cmd.contains("if [ -f pom.xml ]"));
        assertTrue(cmd.contains("if [ -f package.json ]"));
    }

    @Test
    void dependencyAnalysisCommandWithNullStackAndNullModule() {
        String cmd = DependencyScanService.dependencyAnalysisCommand(null, null);
        assertTrue(cmd.contains("if [ -f pom.xml ]"),
                "Auto-detect at root should check for pom.xml, got: " + cmd);
        assertTrue(cmd.contains("if [ -f package.json ]"),
                "Auto-detect at root should check for package.json, got: " + cmd);
    }

    // =========== normalizeModulePath ===========

    @Test
    void normalizeModulePathNull() {
        assertNull(DependencyScanService.normalizeModulePath(null));
    }

    @Test
    void normalizeModulePathBlank() {
        assertNull(DependencyScanService.normalizeModulePath("  "));
    }

    @Test
    void normalizeModulePathTrimsTrailingSlash() {
        assertEquals("backend", DependencyScanService.normalizeModulePath("backend/"));
    }

    @Test
    void normalizeModulePathTrimsMultipleTrailingSlashes() {
        assertEquals("frontend", DependencyScanService.normalizeModulePath("frontend///"));
    }

    @Test
    void normalizeModulePathKeepsInternalPath() {
        assertEquals("frontend/src", DependencyScanService.normalizeModulePath("frontend/src/"));
    }

    @Test
    void normalizeModulePathTrimsWhitespace() {
        assertEquals("backend", DependencyScanService.normalizeModulePath(" backend/ "));
    }
}