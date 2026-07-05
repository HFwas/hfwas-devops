package com.hfwas.devops.pm.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.ProjectPageDto;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.model.ProjectAccessContextVO;
import com.hfwas.devops.pm.project.service.ProjectService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pm/projects")
@RequiredArgsConstructor
public class PmProjectController {

    private final ProjectService projectService;

    @PostMapping("/page")
    public BaseResult<IPage<PmProject>> page(@RequestBody ProjectPageDto dto) {
        return BaseResult.ok(projectService.page(dto, dto.getKeyword()));
    }

    @OperLog(module = "pm", action = "save", bizType = "project", summary = "保存项目", bizId = "#result.data")
    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmProject project) {
        return BaseResult.ok(projectService.save(project));
    }

    @GetMapping("/{id}/access-context")
    public BaseResult<ProjectAccessContextVO> accessContext(@PathVariable Long id) {
        return BaseResult.ok(projectService.resolveAccessContext(id));
    }

    @GetMapping("/{id}")
    public BaseResult<PmProject> getById(@PathVariable Long id) {
        return BaseResult.ok(projectService.getById(id));
    }

    @OperLog(module = "pm", action = "delete", bizType = "project", summary = "删除项目", bizId = "#id")
    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        projectService.delete(id);
        return BaseResult.ok();
    }
}
