package com.hfwas.devops.apitest.environment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.environment.dto.EnvironmentCreateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentQueryDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentUpdateDTO;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.environment.vo.EnvironmentDetailVO;
import com.hfwas.devops.apitest.environment.vo.EnvironmentVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 环境变量控制器
 *
 * @author hfwas
 */
@Tag(name = "环境变量管理")
@RestController
@RequestMapping("/apitest/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @Operation(summary = "分页查询环境列表")
    @GetMapping("/page")
    public BaseResult<IPage<EnvironmentVO>> pageQuery(@Valid EnvironmentQueryDTO query) {
        return BaseResult.ok(environmentService.pageQuery(query));
    }

    @Operation(summary = "查询所有环境列表（不分页）")
    @GetMapping("/list")
    public BaseResult<List<EnvironmentVO>> listAll(@RequestParam Long projectId) {
        return BaseResult.ok(environmentService.listAll(projectId));
    }

    @Operation(summary = "获取环境详情")
    @GetMapping("/{id}")
    public BaseResult<EnvironmentDetailVO> getDetail(@PathVariable Long id) {
        return BaseResult.ok(environmentService.getDetail(id));
    }

    @Operation(summary = "创建环境")
    @PostMapping
    public BaseResult<EnvironmentDetailVO> create(@Valid @RequestBody EnvironmentCreateDTO dto,
                                                   @RequestParam Long projectId,
                                                   @RequestParam Long userId) {
        return BaseResult.ok(environmentService.create(dto, projectId, userId));
    }

    @Operation(summary = "更新环境")
    @PutMapping("/{id}")
    public BaseResult<EnvironmentDetailVO> update(@PathVariable Long id,
                                                   @Valid @RequestBody EnvironmentUpdateDTO dto,
                                                   @RequestParam Long userId) {
        return BaseResult.ok(environmentService.update(id, dto, userId));
    }

    @Operation(summary = "删除环境")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        environmentService.delete(id);
        return BaseResult.ok();
    }
}