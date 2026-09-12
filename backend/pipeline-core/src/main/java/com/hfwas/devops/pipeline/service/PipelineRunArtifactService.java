package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineRunArtifactVO;
import com.hfwas.devops.pipeline.entity.PipelineRunArtifactEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunArtifactMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineRunArtifactService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunArtifactService.class);

    private final PipelineRunArtifactMapper artifactMapper;
    private final PipelineRunMapper runMapper;
    private final SbomParserService sbomParserService;

    @Value("${pipeline.artifact.storage:data/artifacts}")
    private String storageBase;

    public PipelineRunArtifactService(PipelineRunArtifactMapper artifactMapper, PipelineRunMapper runMapper, SbomParserService sbomParserService) {
        this.artifactMapper = artifactMapper;
        this.runMapper = runMapper;
        this.sbomParserService = sbomParserService;
    }

    @Transactional
    public PipelineRunArtifactVO upload(Long runId, Long jobId, String artifactType, MultipartFile file) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "运行记录不存在");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "sbom.json";
        }
        long fileSize = file.getSize();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/json";
        }

        // Generate unique storage path
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String storagePath = String.format("%d/%s/%s", runId, uuid, fileName);

        // Save file to disk
        try {
            Path targetDir = Paths.get(storageBase, String.valueOf(runId), uuid);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("保存 artifact 文件失败: runId={}, name={}", runId, fileName, e);
            throw BizException.of(ResultCode.INTERNAL_ERROR, "保存文件失败: " + e.getMessage());
        }

        PipelineRunArtifactEntity entity = new PipelineRunArtifactEntity();
        entity.setRunId(runId);
        entity.setJobId(jobId);
        entity.setArtifactType(artifactType != null ? artifactType : "sbom");
        entity.setFileName(fileName);
        entity.setFileSize(fileSize);
        entity.setStoragePath(storagePath);
        entity.setContentType(contentType);
        entity.setCreateTime(LocalDateTime.now());
        artifactMapper.insert(entity);

        // 异步解析 SBOM 组件入库
        if ("sbom".equals(artifactType) || (fileName != null && fileName.contains("sbom"))) {
            parseSbomAsync(entity.getId(), runId, entity.getStoragePath());
        }

        return toVo(entity);
    }

    private void parseSbomAsync(Long artifactId, Long runId, String storagePath) {
        try {
            java.nio.file.Path file = Paths.get(storageBase, storagePath);
            if (java.nio.file.Files.exists(file)) {
                try (InputStream in = java.nio.file.Files.newInputStream(file)) {
                    sbomParserService.parse(artifactId, runId, in);
                }
            }
        } catch (Exception e) {
            log.warn("SBOM 异步解析失败: artifactId={}, runId={}", artifactId, runId, e);
        }
    }

    public List<PipelineRunArtifactVO> listByRun(Long runId) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "运行记录不存在");
        }
        return artifactMapper.selectList(new LambdaQueryWrapper<PipelineRunArtifactEntity>()
                .eq(PipelineRunArtifactEntity::getRunId, runId)
                .orderByDesc(PipelineRunArtifactEntity::getCreateTime))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public PipelineRunArtifactEntity getById(Long artifactId) {
        PipelineRunArtifactEntity entity = artifactMapper.selectById(artifactId);
        if (entity == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "产物不存在");
        }
        return entity;
    }

    public PipelineRunArtifactEntity getSbomByRun(Long runId) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<PipelineRunArtifactEntity>()
                .eq(PipelineRunArtifactEntity::getRunId, runId)
                .eq(PipelineRunArtifactEntity::getArtifactType, "sbom")
                .orderByDesc(PipelineRunArtifactEntity::getCreateTime)
                .last("LIMIT 1"));
    }

    public InputStream openStream(PipelineRunArtifactEntity entity) {
        try {
            Path file = Paths.get(storageBase, entity.getStoragePath());
            if (!Files.exists(file)) {
                throw BizException.of(ResultCode.NOT_FOUND, "产物文件不存在: " + entity.getStoragePath());
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "读取产物失败: " + e.getMessage());
        }
    }

    private PipelineRunArtifactVO toVo(PipelineRunArtifactEntity entity) {
        PipelineRunArtifactVO vo = new PipelineRunArtifactVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setJobId(entity.getJobId());
        vo.setArtifactType(entity.getArtifactType());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setContentType(entity.getContentType());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}