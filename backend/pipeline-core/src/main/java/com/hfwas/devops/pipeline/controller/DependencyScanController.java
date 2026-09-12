package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.BatchDependencyScanSubmitDTO;
import com.hfwas.devops.pipeline.dto.DependencyScanSubmitDTO;
import com.hfwas.devops.pipeline.dto.DependencyScanVO;
import com.hfwas.devops.pipeline.service.DependencyScanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dependency-scan")
public class DependencyScanController {

    private final DependencyScanService scanService;

    public DependencyScanController(DependencyScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/submit")
    public BaseResult<DependencyScanVO> submit(@RequestBody DependencyScanSubmitDTO dto) {
        return BaseResult.ok(scanService.submitSingle(dto));
    }

    @PostMapping("/batch-submit")
    public BaseResult<List<DependencyScanVO>> batchSubmit(@RequestBody BatchDependencyScanSubmitDTO dto) {
        return BaseResult.ok(scanService.submitBatch(dto.getScans()));
    }

    @GetMapping("/tasks")
    public BaseResult<List<DependencyScanVO>> listTasks(@RequestParam(defaultValue = "20") int limit) {
        return BaseResult.ok(scanService.listRecent(limit));
    }
}