package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.workitem.model.BatchActionConfigVO;
import com.hfwas.devops.pm.workitem.service.BatchActionConfigService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pm/projects/{projectId}/batch-action-configs")
@RequiredArgsConstructor
public class PmBatchActionConfigController {

    private final BatchActionConfigService configService;

    @GetMapping
    public BaseResult<List<BatchActionConfigVO>> list(@PathVariable Long projectId) {
        return BaseResult.ok(configService.list(projectId));
    }

    @OperLog(module = "pm", action = "save", bizType = "batch_action_config", summary = "保存批量操作配置")
    @PostMapping
    public BaseResult<BatchActionConfigVO> save(@PathVariable Long projectId,
                                                @RequestBody BatchActionConfigVO dto) {
        return BaseResult.ok(configService.save(projectId, dto));
    }
}
