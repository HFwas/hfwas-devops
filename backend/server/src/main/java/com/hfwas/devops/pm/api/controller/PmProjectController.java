package com.hfwas.devops.pm.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.api.dto.ProjectPageDto;
import com.hfwas.devops.pm.project.entity.PmProject;
import com.hfwas.devops.pm.project.service.ProjectService;
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

    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmProject project) {
        return BaseResult.ok(projectService.save(project));
    }

    @GetMapping("/{id}")
    public BaseResult<PmProject> getById(@PathVariable Long id) {
        return BaseResult.ok(projectService.getById(id));
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam Long id) {
        projectService.delete(id);
        return BaseResult.ok();
    }
}
