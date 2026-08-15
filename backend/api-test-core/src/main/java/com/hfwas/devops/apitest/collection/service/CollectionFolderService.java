package com.hfwas.devops.apitest.collection.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.collection.dto.CollectionFolderCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionFolderUpdateDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionFolderEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionFolderMapper;
import com.hfwas.devops.apitest.collection.vo.CollectionFolderVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 集合文件夹业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionFolderService extends ServiceImpl<CollectionFolderMapper, CollectionFolderEntity> {

    private final CollectionService collectionService;

    /**
     * 创建文件夹
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionFolderVO create(Long collectionId, CollectionFolderCreateDTO dto, Long userId) {
        // 校验集合是否存在
        CollectionEntity collection = collectionService.getById(collectionId);
        if (collection == null) {
            throw new ApiTestException("集合不存在");
        }

        // 校验父文件夹是否属于同一集合
        if (dto.getParentId() != null) {
            CollectionFolderEntity parent = getById(dto.getParentId());
            if (parent == null || !parent.getCollectionId().equals(collectionId)) {
                throw new ApiTestException("父文件夹不存在或不属于该集合");
            }
        }

        // 校验文件夹名是否重复
        boolean exists = lambdaQuery()
                .eq(CollectionFolderEntity::getCollectionId, collectionId)
                .eq(CollectionFolderEntity::getName, dto.getName())
                .exists();
        if (exists) {
            throw new ApiTestException("该集合下已存在同名文件夹");
        }

        CollectionFolderEntity entity = new CollectionFolderEntity();
        entity.setCollectionId(collectionId);
        entity.setParentId(dto.getParentId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        save(entity);

        log.info("创建文件夹: id={}, name={}, collectionId={}", entity.getId(), entity.getName(), collectionId);

        return buildFolderVO(entity);
    }

    /**
     * 更新文件夹
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionFolderVO update(Long collectionId, Long folderId, CollectionFolderUpdateDTO dto, Long userId) {
        CollectionFolderEntity entity = getById(folderId);
        if (entity == null || !entity.getCollectionId().equals(collectionId)) {
            throw new ApiTestException("文件夹不存在");
        }

        if (StrUtil.isNotBlank(dto.getName())) {
            // 校验名称唯一性
            boolean exists = lambdaQuery()
                    .eq(CollectionFolderEntity::getCollectionId, collectionId)
                    .eq(CollectionFolderEntity::getName, dto.getName())
                    .ne(CollectionFolderEntity::getId, folderId)
                    .exists();
            if (exists) {
                throw new ApiTestException("该集合下已存在同名文件夹");
            }
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getSortOrder() != null) {
            entity.setSortOrder(dto.getSortOrder());
        }
        updateById(entity);

        log.info("更新文件夹: id={}, name={}", folderId, entity.getName());
        return buildFolderVO(entity);
    }

    /**
     * 删除文件夹
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long collectionId, Long folderId) {
        CollectionFolderEntity entity = getById(folderId);
        if (entity == null || !entity.getCollectionId().equals(collectionId)) {
            throw new ApiTestException("文件夹不存在");
        }

        // 检查是否有子文件夹
        boolean hasChildren = lambdaQuery()
                .eq(CollectionFolderEntity::getParentId, folderId)
                .exists();
        if (hasChildren) {
            throw new ApiTestException("请先删除子文件夹");
        }

        removeById(folderId);
        log.info("删除文件夹: id={}, name={}", folderId, entity.getName());
    }

    /**
     * 获取文件夹树
     */
    public List<CollectionFolderVO> getTree(Long collectionId) {
        List<CollectionFolderEntity> allFolders = lambdaQuery()
                .eq(CollectionFolderEntity::getCollectionId, collectionId)
                .orderByAsc(CollectionFolderEntity::getSortOrder)
                .list();

        return buildTree(allFolders, null);
    }

    /**
     * 递归构建树形结构
     */
    private List<CollectionFolderVO> buildTree(List<CollectionFolderEntity> allFolders, Long parentId) {
        List<CollectionFolderVO> result = new ArrayList<>();
        for (CollectionFolderEntity folder : allFolders) {
            if ((parentId == null && folder.getParentId() == null)
                    || (parentId != null && parentId.equals(folder.getParentId()))) {
                CollectionFolderVO vo = buildFolderVO(folder);
                vo.setChildren(buildTree(allFolders, folder.getId()));
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 构建文件夹 VO
     */
    private CollectionFolderVO buildFolderVO(CollectionFolderEntity entity) {
        CollectionFolderVO vo = new CollectionFolderVO();
        vo.setId(entity.getId());
        vo.setCollectionId(entity.getCollectionId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setChildren(new ArrayList<>());
        vo.setItems(new ArrayList<>());
        return vo;
    }
}