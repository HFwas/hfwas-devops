package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.service.PipelineTaskKindService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pipeline/task-kinds")
public class PipelineTaskKindController {

    private final PipelineTaskKindService taskKindService;

    public PipelineTaskKindController(PipelineTaskKindService taskKindService) {
        this.taskKindService = taskKindService;
    }

    @GetMapping
    public BaseResult<List<TaskKindVO>> list() {
        return BaseResult.ok(taskKindService.listAll());
    }

    @GetMapping("/{kind}")
    public BaseResult<TaskKindVO> get(@PathVariable("kind") String kind) {
        return BaseResult.ok(taskKindService.getByKind(kind));
    }

    @PutMapping("/{kind}")
    public BaseResult<Void> update(
            @PathVariable("kind") String kind,
            @Valid @RequestBody TaskKindUpdateDTO dto
    ) {
        taskKindService.update(kind, dto);
        return BaseResult.ok(null);
    }

    @PatchMapping("/{kind}/toggle")
    public BaseResult<Void> toggle(@PathVariable("kind") String kind) {
        taskKindService.toggle(kind);
        return BaseResult.ok(null);
    }
}