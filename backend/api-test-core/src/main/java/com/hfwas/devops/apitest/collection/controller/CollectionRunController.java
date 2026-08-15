package com.hfwas.devops.apitest.collection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.collection.service.CollectionRunService;
import com.hfwas.devops.apitest.collection.vo.CollectionRunDetailVO;
import com.hfwas.devops.apitest.collection.vo.CollectionRunVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集合执行控制器
 *
 * @author hfwas
 */
@Tag(name = "集合执行")
@RestController
@RequestMapping("/apitest/collections")
@RequiredArgsConstructor
public class CollectionRunController {

    private final CollectionRunService runService;

    @Operation(summary = "执行集合")
    @PostMapping("/{collectionId}/run")
    public BaseResult<CollectionRunVO> execute(@PathVariable Long collectionId,
                                                @RequestParam(required = false) Long environmentId,
                                                @RequestParam Long userId) {
        return BaseResult.ok(runService.execute(collectionId, environmentId, userId));
    }

    @Operation(summary = "运行历史列表")
    @GetMapping("/{collectionId}/runs")
    public BaseResult<IPage<CollectionRunVO>> pageQuery(@PathVariable Long collectionId,
                                                         @RequestParam(required = false, defaultValue = "1") Integer pageNo,
                                                         @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return BaseResult.ok(runService.pageQuery(collectionId, pageNo, pageSize));
    }

    @Operation(summary = "运行详情")
    @GetMapping("/runs/{runId}")
    public BaseResult<CollectionRunDetailVO> getDetail(@PathVariable Long runId) {
        return BaseResult.ok(runService.getDetail(runId));
    }

    @Operation(summary = "删除运行记录")
    @DeleteMapping("/runs/{runId}")
    public BaseResult<Void> delete(@PathVariable Long runId) {
        runService.delete(runId);
        return BaseResult.ok();
    }
}