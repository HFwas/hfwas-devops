package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.JobParamDefinitionVO;
import com.hfwas.devops.pipeline.dto.JobParamPreviewDTO;
import com.hfwas.devops.pipeline.dto.JobParamPreviewResultVO;
import com.hfwas.devops.pipeline.dto.JobParamSaveDTO;
import com.hfwas.devops.pipeline.service.PipelineJobParamService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pipeline/job-params")
public class PipelineJobParamController {

    private final PipelineJobParamService jobParamService;

    public PipelineJobParamController(PipelineJobParamService jobParamService) {
        this.jobParamService = jobParamService;
    }

    @GetMapping
    public BaseResult<List<JobParamDefinitionVO>> list(@RequestParam("pipelineId") Long pipelineId) {
        return BaseResult.ok(jobParamService.listByPipeline(pipelineId));
    }

    @GetMapping("/{id}")
    public BaseResult<JobParamDefinitionVO> get(@PathVariable("id") Long id) {
        return BaseResult.ok(jobParamService.getById(id));
    }

    @PostMapping
    public BaseResult<Long> create(@RequestBody JobParamSaveDTO dto) {
        return BaseResult.ok(jobParamService.create(dto));
    }

    @PutMapping("/{id}")
    public BaseResult<Void> update(@PathVariable("id") Long id, @RequestBody JobParamSaveDTO dto) {
        jobParamService.update(id, dto);
        return BaseResult.ok(null);
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable("id") Long id) {
        jobParamService.delete(id);
        return BaseResult.ok(null);
    }

    @PostMapping("/preview-api")
    public BaseResult<JobParamPreviewResultVO> previewApi(@RequestBody JobParamPreviewDTO dto) {
        return BaseResult.ok(jobParamService.previewApi(
                dto.getApiUrl(),
                dto.getApiMethod(),
                dto.getApiHeaders(),
                dto.getApiResponsePath()
        ));
    }
}