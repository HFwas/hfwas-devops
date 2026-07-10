package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.ProjectIssueTypesQueryDto;
import com.hfwas.devops.pm.api.dto.ProjectIssueTypesSaveDto;
import com.hfwas.devops.pm.meta.PmWorkItemType;
import com.hfwas.devops.pm.meta.ProjectIssueTypeService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pm/projects/issue-types")
@RequiredArgsConstructor
public class PmProjectIssueTypeController {

    private final ProjectIssueTypeService projectIssueTypeService;

    @PostMapping("/list")
    public BaseResult<List<PmWorkItemType>> list(@RequestBody ProjectIssueTypesQueryDto dto) {
        return BaseResult.ok(projectIssueTypeService.listForProject(dto.getProjectId()));
    }

    @OperLog(module = "pm", action = "save", bizType = "project_issue_type", summary = "保存项目事项类型方案")
    @PostMapping("/save")
    public BaseResult<Void> save(@RequestBody ProjectIssueTypesSaveDto dto) {
        projectIssueTypeService.saveScheme(dto.getProjectId(), dto.getTypeCodes());
        return BaseResult.ok(null);
    }
}
