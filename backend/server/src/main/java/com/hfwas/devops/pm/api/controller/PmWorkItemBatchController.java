package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.WorkItemBatchTransitionDto;
import com.hfwas.devops.pm.api.dto.WorkItemBatchUpdateDto;
import com.hfwas.devops.pm.workitem.model.BatchOperationResultVO;
import com.hfwas.devops.pm.workitem.service.WorkItemBatchService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pm/work-items")
@RequiredArgsConstructor
public class PmWorkItemBatchController {

    private final WorkItemBatchService batchService;

    @OperLog(module = "pm", action = "batch-update", bizType = "work_item", summary = "批量更新工作项")
    @PostMapping("/batch-update")
    public BaseResult<BatchOperationResultVO> batchUpdate(@RequestBody WorkItemBatchUpdateDto dto) {
        return BaseResult.ok(batchService.batchUpdate(
                dto.getIds(), dto.getPatch(), dto.getVersions(), dto.isDryRun()));
    }

    @OperLog(module = "pm", action = "batch-transition", bizType = "work_item", summary = "批量流转工作项")
    @PostMapping("/batch-transition")
    public BaseResult<BatchOperationResultVO> batchTransition(@RequestBody WorkItemBatchTransitionDto dto) {
        return BaseResult.ok(batchService.batchTransition(
                dto.getIds(), dto.getTransitionId(), dto.getToStatus(),
                dto.getFields(), dto.getVersions(), dto.isDryRun()));
    }
}
