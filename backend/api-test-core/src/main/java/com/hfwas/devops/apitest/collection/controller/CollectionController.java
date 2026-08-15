package com.hfwas.devops.apitest.collection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionUpdateDTO;
import com.hfwas.devops.apitest.collection.service.CollectionService;
import com.hfwas.devops.apitest.collection.vo.CollectionDetailVO;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
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

/**
 * 集合控制器
 *
 * @author hfwas
 */
@Tag(name = "集合管理")
@RestController
@RequestMapping("/apitest/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "分页查询集合列表")
    @GetMapping("/page")
    public BaseResult<IPage<CollectionVO>> pageQuery(@RequestParam Long projectId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false, defaultValue = "1") Integer pageNo,
                                                      @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return BaseResult.ok(collectionService.pageQuery(projectId, keyword, pageNo, pageSize));
    }

    @Operation(summary = "获取集合详情（含树形结构）")
    @GetMapping("/{id}")
    public BaseResult<CollectionDetailVO> getDetail(@PathVariable Long id) {
        return BaseResult.ok(collectionService.getDetail(id));
    }

    @Operation(summary = "创建集合")
    @PostMapping
    public BaseResult<CollectionVO> create(@Valid @RequestBody CollectionCreateDTO dto,
                                            @RequestParam Long projectId,
                                            @RequestParam Long userId) {
        return BaseResult.ok(collectionService.create(dto, projectId, userId));
    }

    @Operation(summary = "更新集合")
    @PutMapping("/{id}")
    public BaseResult<CollectionVO> update(@PathVariable Long id,
                                            @Valid @RequestBody CollectionUpdateDTO dto,
                                            @RequestParam Long userId) {
        return BaseResult.ok(collectionService.update(id, dto, userId));
    }

    @Operation(summary = "删除集合")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        collectionService.delete(id);
        return BaseResult.ok();
    }
}