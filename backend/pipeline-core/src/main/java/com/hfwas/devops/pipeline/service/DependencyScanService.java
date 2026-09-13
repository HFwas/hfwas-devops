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
 * <p>
 * 支持按模块扫描：指定 modulePath（如 backend/、frontend/），
 * 运行时自动检测该模块的语言栈（pom.xml → Maven, package.json → cdxgen），
 * 也可以直接指定 stack 跳过自动检测。
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

        String modulePath = normalizeModulePath(dto.getModulePath());
        String stack = dto.getStack();

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
        buildJob.setCommand(buildCommand(stack, modulePath));
        buildJob.setStack(stack);
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
        analysisJob.setCommand(dependencyAnalysisCommand(stack, modulePath));
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
        return List.of();
    }

    // ========================
    //  命令生成
    // ========================

    /**
     * 构建命令：按 stack 或自动检测
     */
    static String buildCommand(String stack, String modulePath) {
        String prefix = modulePath != null ? "cd " + modulePath + " && " : "";
        if (stack != null && !stack.isBlank()) {
            return prefix + switch (stack) {
                case "JAVA_MAVEN" -> "mvn dependency:resolve -q --no-transfer-progress";
                case "NODE" -> "npm ci || npm install";
                case "GO" -> "go mod download";
                case "PYTHON" ->
                        "pip install -r requirements.txt 2>/dev/null || pip install -r requirements/*.txt 2>/dev/null || echo 'no requirements found'";
                default -> "echo 'no build needed'";
            };
        }
        // stack 为空 → 自动检测
        if (modulePath != null) {
            return prefix + autoDetectBuild();
        }
        return "echo 'no build needed'";
    }

    /**
     * 依赖分析命令：按 stack 或自动检测
     */
    static String dependencyAnalysisCommand(String stack, String modulePath) {
        String prefix = modulePath != null ? "cd " + modulePath + " && " : "";
        if (stack != null && !stack.isBlank()) {
            return prefix + switch (stack) {
                case "JAVA_MAVEN" ->
                        "mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress";
                default -> "cdxgen -o target/sbom.json -t cyclonedx:json";
            };
        }
        // stack 为空 → 运行时自动检测
        return prefix + autoDetectAnalysis();
    }

    /**
     * 自动检测语言栈 + 执行正确的 BUILD 命令（运行时脚本）
     */
    private static String autoDetectBuild() {
        return """
                if [ -f pom.xml ]; then
                  mvn dependency:resolve -q --no-transfer-progress
                elif [ -f package.json ]; then
                  npm ci || npm install
                elif [ -f go.mod ]; then
                  go mod download
                elif [ -f requirements.txt ]; then
                  pip install -r requirements.txt 2>/dev/null || echo 'no requirements found'
                elif [ -f pyproject.toml ]; then
                  pip install -e . 2>/dev/null || echo 'no setup found'
                else
                  echo 'no supported language detected, skip build'
                fi
                """.stripIndent();
    }

    /**
     * 自动检测语言栈 + 执行正确的 DEPENDENCY_ANALYSIS 命令（运行时脚本）
     * <p>
     * Java/Maven → CycloneDX Maven Plugin（精度最高，Maven Resolver API）
     * Node → cdxgen
     * Go/Python/其他 → cdxgen
     */
    private static String autoDetectAnalysis() {
        return """
                if [ -f pom.xml ]; then
                  echo "detected Maven project, using CycloneDX Maven Plugin"
                  mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
                    -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress
                elif [ -f package.json ]; then
                  echo "detected Node.js project, using cdxgen"
                  cdxgen -o target/sbom.json -t cyclonedx:json
                elif [ -f go.mod ]; then
                  echo "detected Go project, using cdxgen"
                  cdxgen -o target/sbom.json -t cyclonedx:json
                elif [ -f requirements.txt ] || [ -f Pipfile ] || [ -f pyproject.toml ]; then
                  echo "detected Python project, using cdxgen"
                  cdxgen -o target/sbom.json -t cyclonedx:json
                else
                  echo "no supported language manifest detected in module, skip dependency analysis"
                  exit 0
                fi
                """.stripIndent();
    }

    // ========================
    //  工具方法
    // ========================

    /**
     * 规范化模块路径：去掉首尾空格/斜杠，保证干净路径
     */
    static String normalizeModulePath(String modulePath) {
        if (modulePath == null || modulePath.isBlank()) {
            return null;
        }
        String normalized = modulePath.trim();
        // 去掉尾部斜杠（保留可能的中间路径如 frontend/src）
        while (normalized.endsWith("/") || normalized.endsWith("\\")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
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