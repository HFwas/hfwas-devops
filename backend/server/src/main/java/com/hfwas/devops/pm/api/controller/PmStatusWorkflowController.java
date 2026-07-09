package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.AllowedTransitionsQueryDto;
import com.hfwas.devops.pm.api.dto.StatusWorkflowQueryDto;
import com.hfwas.devops.pm.api.dto.StatusWorkflowSaveDto;
import com.hfwas.devops.pm.api.dto.TransitionMetaQueryDto;
import com.hfwas.devops.pm.workitem.model.AllowedTransitionsVO;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.model.StatusWorkflowVO;
import com.hfwas.devops.pm.workitem.model.TransitionMetaVO;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionMetaVO;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import com.hfwas.devops.pm.workitem.service.TransitionPostFunctionMetaService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/status/workflow")
@RequiredArgsConstructor
public class PmStatusWorkflowController {

    private final StatusDefinitionService statusDefinitionService;
    private final TransitionPostFunctionMetaService transitionPostFunctionMetaService;

    @PostMapping("/get")
    public BaseResult<StatusWorkflowVO> get(@RequestBody StatusWorkflowQueryDto dto) {
        return BaseResult.ok(statusDefinitionService.getWorkflow(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/options")
    public BaseResult<List<StatusDefinitionVO>> options(@RequestBody StatusWorkflowQueryDto dto) {
        return BaseResult.ok(statusDefinitionService.listStatusOptions(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/allowed")
    public BaseResult<AllowedTransitionsVO> allowed(@RequestBody AllowedTransitionsQueryDto dto) {
        return BaseResult.ok(statusDefinitionService.allowedTransitions(
                dto.getProjectId(), dto.getTypeCode(), dto.getFromStatus()));
    }

    @PostMapping("/post-function-meta")
    public BaseResult<TransitionPostFunctionMetaVO> postFunctionMeta(@RequestBody StatusWorkflowQueryDto dto) {
        return BaseResult.ok(transitionPostFunctionMetaService.getMeta(dto.getProjectId(), dto.getTypeCode()));
    }

    @PostMapping("/transition-meta")
    public BaseResult<TransitionMetaVO> transitionMeta(@RequestBody TransitionMetaQueryDto dto) {
        return BaseResult.ok(transitionPostFunctionMetaService.getTransitionMeta(
                dto.getProjectId(), dto.getTypeCode(), dto.getFromStatus(), dto.getTransitionId()));
    }

    @OperLog(module = "pm", action = "save", bizType = "status_workflow", summary = "保存状态流转配置")
    @PostMapping("/save")
    public BaseResult<Void> save(@RequestBody StatusWorkflowSaveDto dto) {
        statusDefinitionService.saveWorkflow(dto.getProjectId(), dto.getTypeCode(), dto.getStatuses());
        return BaseResult.ok(null);
    }

    @OperLog(module = "pm", action = "reset", bizType = "status_workflow", summary = "重置状态流转配置")
    @PostMapping("/reset")
    public BaseResult<Void> reset(@RequestBody StatusWorkflowQueryDto dto) {
        statusDefinitionService.resetWorkflow(dto.getProjectId(), dto.getTypeCode());
        return BaseResult.ok(null);
    }
}
