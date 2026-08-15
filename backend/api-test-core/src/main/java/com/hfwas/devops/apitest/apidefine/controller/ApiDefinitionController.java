package com.hfwas.devops.apitest.apidefine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionQueryDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionUpdateDTO;
import com.hfwas.devops.apitest.apidefine.service.ApiDefinitionService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionDetailVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionVO;
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

/**
 * 接口定义控制器
 *
 * @author hfwas
 */
@Tag(name = "接口定义管理")
@RestController
@RequestMapping("/apitest/definitions")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefinitionService;

    @Operation(summary = "分页查询接口列表")
    @GetMapping("/page")
    public BaseResult<IPage<ApiDefinitionVO>> pageQuery(@Valid ApiDefinitionQueryDTO query) {
        return BaseResult.ok(apiDefinitionService.pageQuery(query));
    }

    @Operation(summary = "获取接口详情")
    @GetMapping("/{id}")
    public BaseResult<ApiDefinitionDetailVO> getDetail(@PathVariable Long id) {
        return BaseResult.ok(apiDefinitionService.getDetail(id));
    }

    @Operation(summary = "创建接口定义")
    @PostMapping
    public BaseResult<ApiDefinitionDetailVO> create(@Valid @RequestBody ApiDefinitionCreateDTO dto,
                                                    @RequestParam Long userId) {
        return BaseResult.ok(apiDefinitionService.create(dto, userId));
    }

    @Operation(summary = "更新接口定义")
    @PutMapping("/{id}")
    public BaseResult<ApiDefinitionDetailVO> update(@PathVariable Long id,
                                                    @Valid @RequestBody ApiDefinitionUpdateDTO dto,
                                                    @RequestParam Long userId) {
        return BaseResult.ok(apiDefinitionService.update(id, dto, userId));
    }

    @Operation(summary = "删除接口定义")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        apiDefinitionService.delete(id);
        return BaseResult.ok();
    }

    @Operation(summary = "发布接口（草稿→已发布）")
    @PostMapping("/{id}/publish")
    public BaseResult<Void> publish(@PathVariable Long id, @RequestParam Long userId) {
        apiDefinitionService.publish(id, userId);
        return BaseResult.ok();
    }

    @Operation(summary = "废弃接口（已发布→已废弃）")
    @PostMapping("/{id}/deprecate")
    public BaseResult<Void> deprecate(@PathVariable Long id, @RequestParam Long userId) {
        apiDefinitionService.deprecate(id, userId);
        return BaseResult.ok();
    }

    @Operation(summary = "恢复草稿（已发布/已废弃→草稿）")
    @PostMapping("/{id}/revert-draft")
    public BaseResult<Void> revertToDraft(@PathVariable Long id, @RequestParam Long userId) {
        apiDefinitionService.revertToDraft(id, userId);
        return BaseResult.ok();
    }
}