package com.hfwas.devops.apitest.collection.controller;

import com.hfwas.devops.apitest.collection.dto.CollectionItemAddDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionItemBatchDTO;
import com.hfwas.devops.apitest.collection.service.CollectionItemService;
import com.hfwas.devops.apitest.collection.vo.CollectionItemVO;
import com.hfwas.devops.common.core.base.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 集合项控制器
 *
 * @author hfwas
 */
@Tag(name = "集合项管理")
@RestController
@RequestMapping("/apitest/collections/{collectionId}/items")
@RequiredArgsConstructor
public class CollectionItemController {

    private final CollectionItemService itemService;

    @Operation(summary = "添加集合项")
    @PostMapping
    public BaseResult<CollectionItemVO> add(@PathVariable Long collectionId,
                                             @Valid @RequestBody CollectionItemAddDTO dto,
                                             @RequestParam Long userId) {
        return BaseResult.ok(itemService.add(collectionId, dto, userId));
    }

    @Operation(summary = "更新集合项")
    @PutMapping("/{itemId}")
    public BaseResult<CollectionItemVO> update(@PathVariable Long collectionId,
                                                @PathVariable Long itemId,
                                                @RequestBody CollectionItemAddDTO dto) {
        return BaseResult.ok(itemService.update(collectionId, itemId, dto));
    }

    @Operation(summary = "删除集合项")
    @DeleteMapping("/{itemId}")
    public BaseResult<Void> delete(@PathVariable Long collectionId,
                                    @PathVariable Long itemId) {
        itemService.delete(collectionId, itemId);
        return BaseResult.ok();
    }

    @Operation(summary = "重排序")
    @PutMapping("/reorder")
    public BaseResult<Void> reorder(@PathVariable Long collectionId,
                                     @RequestBody List<Long> itemIds) {
        itemService.reorder(collectionId, itemIds);
        return BaseResult.ok();
    }

    @Operation(summary = "批量添加")
    @PostMapping("/batch")
    public BaseResult<Void> batchAdd(@PathVariable Long collectionId,
                                      @Valid @RequestBody CollectionItemBatchDTO dto,
                                      @RequestParam Long userId) {
        itemService.batchAdd(collectionId, dto, userId);
        return BaseResult.ok();
    }
}