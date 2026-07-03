package com.hfwas.devops.pm.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pm.module.entity.PmProjectModule;
import com.hfwas.devops.pm.module.service.ProjectModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/project-modules")
@RequiredArgsConstructor
public class PmProjectModuleController {

    private final ProjectModuleService moduleService;

    @GetMapping("/tree")
    public BaseResult<List<PmProjectModule>> tree(@RequestParam("projectId") Long projectId) {
        return BaseResult.ok(moduleService.listTree(projectId));
    }

    @GetMapping("/flat")
    public BaseResult<List<PmProjectModule>> flat(@RequestParam("projectId") Long projectId) {
        return BaseResult.ok(moduleService.listFlat(projectId));
    }

    @PostMapping("/save")
    public BaseResult<Long> save(@RequestBody PmProjectModule module) {
        return BaseResult.ok(moduleService.save(module));
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        moduleService.delete(id);
        return BaseResult.ok();
    }
}
