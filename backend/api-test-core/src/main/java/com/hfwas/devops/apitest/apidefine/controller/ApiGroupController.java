package com.hfwas.devops.apitest.apidefine.controller;

import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupUpdateDTO;
import com.hfwas.devops.apitest.apidefine.service.ApiGroupService;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 接口分组控制器
 *
 * @author hfwas
 */
@Tag(name = "接口分组管理")
@RestController
@RequestMapping("/apitest/groups")
@RequiredArgsConstructor
public class ApiGroupController {

    private final ApiGroupService apiGroupService;

    @Operation(summary = "创建分组")
    @PostMapping
    public BaseResult<ApiGroupVO> create(@Valid @RequestBody ApiGroupCreateDTO dto,
                                         @RequestParam Long userId) {
        return BaseResult.ok(apiGroupService.create(dto, userId));
    }

    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public BaseResult<ApiGroupVO> update(@PathVariable Long id,
                                         @Valid @RequestBody ApiGroupUpdateDTO dto,
                                         @RequestParam Long userId) {
        return BaseResult.ok(apiGroupService.update(id, dto, userId));
    }

    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        apiGroupService.delete(id);
        return BaseResult.ok();
    }

    @Operation(summary = "获取分组树")
    @GetMapping("/tree")
    public BaseResult<List<ApiGroupVO>> getTree(@RequestParam Long projectId) {
        return BaseResult.ok(apiGroupService.getGroupTree(projectId));
    }

    @Operation(summary = "获取分组详情")
    @GetMapping("/{id}")
    public BaseResult<ApiGroupVO> getDetail(@PathVariable Long id) {
        return BaseResult.ok(apiGroupService.getDetail(id));
    }
}