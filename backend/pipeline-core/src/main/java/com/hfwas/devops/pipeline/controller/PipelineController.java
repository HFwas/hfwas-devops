package com.hfwas.devops.pipeline.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.PipelinePageQuery;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.dto.PipelineSaveDTO;
import com.hfwas.devops.pipeline.dto.PipelineVO;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import com.hfwas.devops.pipeline.service.PipelineRunService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pipeline/pipelines")
public class PipelineController {

    private final PipelineDefinitionService definitionService;
    private final PipelineRunService runService;

    public PipelineController(PipelineDefinitionService definitionService, PipelineRunService runService) {
        this.definitionService = definitionService;
        this.runService = runService;
    }

    @PostMapping("/page")
    public BaseResult<IPage<PipelineVO>> page(@RequestBody PipelinePageQuery query) {
        return BaseResult.ok(definitionService.page(query));
    }

    @PostMapping
    public BaseResult<Long> create(@RequestBody PipelineSaveDTO dto) {
        dto.setId(null);
        return BaseResult.ok(definitionService.save(dto));
    }

    @GetMapping("/{id}")
    public BaseResult<PipelineVO> get(@PathVariable("id") Long id) {
        return BaseResult.ok(definitionService.get(id));
    }

    @PutMapping("/{id}")
    public BaseResult<Long> update(@PathVariable("id") Long id, @RequestBody PipelineSaveDTO dto) {
        dto.setId(id);
        return BaseResult.ok(definitionService.save(dto));
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable("id") Long id) {
        definitionService.delete(id);
        return BaseResult.ok(null);
    }

    @PostMapping("/{id}/runs")
    public BaseResult<PipelineRunVO> start(@PathVariable("id") Long id) {
        return BaseResult.ok(runService.start(id));
    }

    @GetMapping("/{id}/runs/{runId}")
    public BaseResult<PipelineRunVO> getRun(@PathVariable("id") Long id, @PathVariable("runId") Long runId) {
        return BaseResult.ok(runService.get(id, runId));
    }

    @PostMapping("/{id}/runs/{runId}/cancel")
    public BaseResult<PipelineRunVO> cancel(@PathVariable("id") Long id, @PathVariable("runId") Long runId) {
        return BaseResult.ok(runService.cancel(id, runId));
    }

    @PostMapping("/{id}/runs/{runId}/approve")
    public BaseResult<PipelineRunVO> approve(@PathVariable("id") Long id, @PathVariable("runId") Long runId) {
        return BaseResult.ok(runService.approve(id, runId));
    }
}
