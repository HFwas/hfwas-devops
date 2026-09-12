package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.PipelineRunArtifactVO;
import com.hfwas.devops.pipeline.entity.PipelineRunArtifactEntity;
import com.hfwas.devops.pipeline.service.PipelineRunArtifactService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/pipeline/runs/{runId}/artifacts")
public class PipelineRunArtifactController {

    private final PipelineRunArtifactService artifactService;

    public PipelineRunArtifactController(PipelineRunArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PostMapping
    public BaseResult<PipelineRunArtifactVO> upload(
            @PathVariable("runId") Long runId,
            @RequestParam(value = "jobId", required = false) Long jobId,
            @RequestParam(value = "type", defaultValue = "sbom") String type,
            @RequestParam("file") MultipartFile file
    ) {
        return BaseResult.ok(artifactService.upload(runId, jobId, type, file));
    }

    @GetMapping
    public BaseResult<List<PipelineRunArtifactVO>> list(@PathVariable("runId") Long runId) {
        return BaseResult.ok(artifactService.listByRun(runId));
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("runId") Long runId,
            @PathVariable("artifactId") Long artifactId
    ) {
        PipelineRunArtifactEntity entity = artifactService.getById(artifactId);
        InputStream stream = artifactService.openStream(entity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(entity.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentType(MediaType.parseMediaType(entity.getContentType()));
        headers.setContentLength(entity.getFileSize());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/sbom")
    public BaseResult<PipelineRunArtifactVO> getSbom(@PathVariable("runId") Long runId) {
        PipelineRunArtifactEntity entity = artifactService.getSbomByRun(runId);
        if (entity == null) {
            return BaseResult.ok(null);
        }
        return BaseResult.ok(toVo(entity));
    }

    private static PipelineRunArtifactVO toVo(PipelineRunArtifactEntity entity) {
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