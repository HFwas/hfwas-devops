package com.hfwas.devops.pipeline.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.DependencyScanSubmitDTO;
import com.hfwas.devops.pipeline.dto.DependencyScanVO;
import com.hfwas.devops.pipeline.dto.PipelineJobDTO;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.dto.PipelineSaveDTO;
import com.hfwas.devops.pipeline.dto.PipelineStageDTO;
import com.hfwas.devops.pipeline.mapper.PipelineRunArtifactMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.entity.PipelineRunArtifactEntity;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量依赖扫描服务 — 创建精简流水线（CLONE + BUILD + DEPENDENCY_ANALYSIS）
 * 用户无需手动配置流水线，提交仓库 URL 即可自动扫描依赖。
 */
@Service
public class DependencyScanService {

    private final PipelineDefinitionService definitionService;
    private final PipelineRunService runService;
    private final PipelineRunMapper runMapper;
    private final PipelineRunArtifactMapper artifactMapper;
    private final CurrentUserAccessor currentUserAccessor;

    public DependencyScanService(
            PipelineDefinitionService definitionService,
            PipelineRunService runService,
            PipelineRunMapper runMapper,
            PipelineRunArtifactMapper artifactMapper,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.definitionService = definitionService;
        this.runService = runService;
        this.runMapper = runMapper;
        this.artifactMapper = artifactMapper;
        this.currentUserAccessor = currentUserAccessor;
    }

    @Transactional
    public DependencyScanVO submitSingle(DependencyScanSubmitDTO dto) {
        // 1. 自动生成流水线名称
        String repoName = extractRepoName(dto.getRepoUrl());
        String pipelineName = "[依赖扫描] " + repoName;

        // 2. 创建流水线（CLONE + BUILD + DEPENDENCY_ANALYSIS）
        PipelineSaveDTO pipelineDto = new PipelineSaveDTO();
        pipelineDto.setName(pipelineName);
        pipelineDto.setRepoUrl(dto.getRepoUrl());
        pipelineDto.setGitRef(dto.getGitRef() != null ? dto.getGitRef() : "main");
        pipelineDto.setCredentialId(dto.getCredentialId());

        List<PipelineStageDTO> stages = new ArrayList<>();

        // Stage 1: 代码克隆
        PipelineStageDTO cloneStage = new PipelineStageDTO();
        cloneStage.setName("代码克隆");
        cloneStage.setSortOrder(1);
        PipelineJobDTO cloneJob = new PipelineJobDTO();
        cloneJob.setName("Clone");
        cloneJob.setKind("CLONE");
        cloneJob.setCommand("git clone");
        cloneJob.setSortOrder(1);
        cloneStage.setJobs(List.of(cloneJob));
        stages.add(cloneStage);

        // Stage 2: 构建（安装依赖）
        PipelineStageDTO buildStage = new PipelineStageDTO();
        buildStage.setName("安装依赖");
        buildStage.setSortOrder(2);
        PipelineJobDTO buildJob = new PipelineJobDTO();
        buildJob.setName("Build");
        buildJob.setKind("BUILD");
        String buildCommand = defaultBuildCommand(dto.getStack());
        buildJob.setCommand(buildCommand);
        buildJob.setStack(dto.getStack());
        buildJob.setRuntimeVersion(dto.getRuntimeVersion());
        buildJob.setSortOrder(1);
        buildStage.setJobs(List.of(buildJob));
        stages.add(buildStage);

        // Stage 3: 依赖分析
        PipelineStageDTO analysisStage = new PipelineStageDTO();
        analysisStage.setName("依赖分析");
        analysisStage.setSortOrder(3);
        PipelineJobDTO analysisJob = new PipelineJobDTO();
        analysisJob.setName("DependencyAnalysis");
        analysisJob.setKind("DEPENDENCY_ANALYSIS");

        String stack = dto.getStack();
        if ("JAVA_MAVEN".equals(stack)) {
            analysisJob.setCommand("mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress");
        } else {
            analysisJob.setCommand("cdxgen -o target/sbom.json -t cyclonedx:json");
        }
        analysisJob.setSortOrder(1);
        analysisStage.setJobs(List.of(analysisJob));
        stages.add(analysisStage);

        pipelineDto.setStages(stages);

        // 3. 保存流水线
        Long pipelineId = definitionService.save(pipelineDto);

        // 4. 启动运行
        PipelineRunVO run = runService.start(pipelineId);

        // 5. 返回扫描结果
        return toScanVo(pipelineId, pipelineName, run);
    }

    @Transactional
    public List<DependencyScanVO> submitBatch(List<DependencyScanSubmitDTO> scans) {
        List<DependencyScanVO> results = new ArrayList<>();
        for (DependencyScanSubmitDTO scan : scans) {
            results.add(submitSingle(scan));
        }
        return results;
    }

    public List<DependencyScanVO> listRecent(int limit) {
        // 查询最近创建的依赖扫描流水线及其最近一次运行
        // 简化为查询当前用户最近运行的流水线
        return List.of();
    }

    private static String defaultBuildCommand(String stack) {
        if (stack == null) return "echo 'no build needed'";
        return switch (stack) {
            case "JAVA_MAVEN" -> "mvn dependency:resolve -q --no-transfer-progress";
            case "NODE" -> "npm ci || npm install";
            case "GO" -> "go mod download";
            case "PYTHON" -> "pip install -r requirements.txt 2>/dev/null || pip install -r requirements/*.txt 2>/dev/null || echo 'no requirements found'";
            default -> "echo 'no build needed'";
        };
    }

    private static String extractRepoName(String repoUrl) {
        if (repoUrl == null) return "unknown";
        int lastSlash = repoUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < repoUrl.length() - 1) {
            String name = repoUrl.substring(lastSlash + 1);
            if (name.endsWith(".git")) {
                name = name.substring(0, name.length() - 4);
            }
            return name;
        }
        return repoUrl;
    }

    private static DependencyScanVO toScanVo(Long pipelineId, String pipelineName, PipelineRunVO run) {
        DependencyScanVO vo = new DependencyScanVO();
        vo.setPipelineId(pipelineId);
        vo.setPipelineName(pipelineName);
        vo.setRunId(run.getId());
        vo.setStatus(run.getStatus());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setTriggeredByName(run.getTriggeredByName());
        return vo;
    }
}