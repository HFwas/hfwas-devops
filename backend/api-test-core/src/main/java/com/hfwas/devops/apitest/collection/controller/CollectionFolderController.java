package com.hfwas.devops.apitest.collection.controller;

import com.hfwas.devops.apitest.collection.dto.CollectionFolderCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionFolderUpdateDTO;
import com.hfwas.devops.apitest.collection.service.CollectionFolderService;
import com.hfwas.devops.apitest.collection.vo.CollectionFolderVO;
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
 * 集合文件夹控制器
 *
 * @author hfwas
 */
@Tag(name = "集合文件夹管理")
@RestController
@RequestMapping("/apitest/collections/{collectionId}/folders")
@RequiredArgsConstructor
public class CollectionFolderController {

    private final CollectionFolderService folderService;

    @Operation(summary = "创建文件夹")
    @PostMapping
    public BaseResult<CollectionFolderVO> create(@PathVariable Long collectionId,
                                                  @Valid @RequestBody CollectionFolderCreateDTO dto,
                                                  @RequestParam Long userId) {
        return BaseResult.ok(folderService.create(collectionId, dto, userId));
    }

    @Operation(summary = "更新文件夹")
    @PutMapping("/{folderId}")
    public BaseResult<CollectionFolderVO> update(@PathVariable Long collectionId,
                                                  @PathVariable Long folderId,
                                                  @Valid @RequestBody CollectionFolderUpdateDTO dto,
                                                  @RequestParam Long userId) {
        return BaseResult.ok(folderService.update(collectionId, folderId, dto, userId));
    }

    @Operation(summary = "删除文件夹")
    @DeleteMapping("/{folderId}")
    public BaseResult<Void> delete(@PathVariable Long collectionId,
                                    @PathVariable Long folderId) {
        folderService.delete(collectionId, folderId);
        return BaseResult.ok();
    }

    @Operation(summary = "获取文件夹树")
    @GetMapping("/tree")
    public BaseResult<List<CollectionFolderVO>> getTree(@PathVariable Long collectionId) {
        return BaseResult.ok(folderService.getTree(collectionId));
    }
}