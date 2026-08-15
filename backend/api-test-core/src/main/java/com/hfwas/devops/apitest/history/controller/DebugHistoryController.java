package com.hfwas.devops.apitest.history.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.apidefine.vo.ApiDebugHistoryVO;
import com.hfwas.devops.apitest.history.dto.DebugHistoryQueryDTO;
import com.hfwas.devops.apitest.history.service.DebugHistoryService;
import com.hfwas.devops.apitest.history.vo.DebugHistoryDetailVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 调试历史控制器
 *
 * @author hfwas
 */
@Tag(name = "调试历史")
@RestController
@RequestMapping("/apitest/debug-histories")
@RequiredArgsConstructor
public class DebugHistoryController {

    private final DebugHistoryService debugHistoryService;

    @Operation(summary = "分页查询调试历史")
    @GetMapping("/page")
    public BaseResult<IPage<ApiDebugHistoryVO>> pageQuery(DebugHistoryQueryDTO query) {
        return BaseResult.ok(debugHistoryService.pageQuery(query));
    }

    @Operation(summary = "获取调试历史详情")
    @GetMapping("/{id}")
    public BaseResult<DebugHistoryDetailVO> getDetail(@PathVariable Long id) {
        return BaseResult.ok(debugHistoryService.getDetail(id));
    }

    @Operation(summary = "查询某接口的调试历史")
    @GetMapping("/by-definition")
    public BaseResult<List<ApiDebugHistoryVO>> getByDefinitionId(@RequestParam Long definitionId,
                                                                   @RequestParam(defaultValue = "20") int limit) {
        return BaseResult.ok(debugHistoryService.getByDefinitionId(definitionId, limit));
    }

    @Operation(summary = "删除调试历史")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        debugHistoryService.delete(id);
        return BaseResult.ok();
    }

    @Operation(summary = "批量删除调试历史")
    @DeleteMapping("/batch")
    public BaseResult<Void> deleteBatch(@RequestParam List<Long> ids) {
        debugHistoryService.deleteBatch(ids);
        return BaseResult.ok();
    }
}