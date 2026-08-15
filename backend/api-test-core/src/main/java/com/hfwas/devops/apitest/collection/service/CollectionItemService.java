package com.hfwas.devops.apitest.collection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.collection.dto.CollectionItemAddDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionItemBatchDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionItemEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionItemMapper;
import com.hfwas.devops.apitest.collection.vo.CollectionItemVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 集合项业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionItemService extends ServiceImpl<CollectionItemMapper, CollectionItemEntity> {

    private final CollectionService collectionService;
    private final ApiDefinitionMapper definitionMapper;

    /**
     * 添加集合项
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionItemVO add(Long collectionId, CollectionItemAddDTO dto, Long userId) {
        // 校验集合是否存在
        CollectionEntity collection = collectionService.getById(collectionId);
        if (collection == null) {
            throw new ApiTestException("集合不存在");
        }

        // 校验接口定义是否存在
        ApiDefinitionEntity definition = definitionMapper.selectById(dto.getDefinitionId());
        if (definition == null) {
            throw new ApiTestException("接口定义不存在");
        }

        // 检查是否已添加（防重复）
        boolean exists = lambdaQuery()
                .eq(CollectionItemEntity::getCollectionId, collectionId)
                .eq(CollectionItemEntity::getDefinitionId, dto.getDefinitionId())
                .exists();
        if (exists) {
            throw new ApiTestException("该接口已添加到集合中");
        }

        // 获取当前最大排序序号
        Integer maxSort = getMaxSortOrder(collectionId, dto.getFolderId());

        CollectionItemEntity entity = new CollectionItemEntity();
        entity.setCollectionId(collectionId);
        entity.setFolderId(dto.getFolderId());
        entity.setDefinitionId(dto.getDefinitionId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.getEnabled() != null && dto.getEnabled() ? 1 : 0);
        entity.setSortOrder(maxSort + 1);
        save(entity);

        log.info("添加集合项: id={}, definitionId={}, collectionId={}", entity.getId(), dto.getDefinitionId(), collectionId);

        return buildItemVO(entity, definition);
    }

    /**
     * 更新集合项
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionItemVO update(Long collectionId, Long itemId, CollectionItemAddDTO dto) {
        CollectionItemEntity entity = getById(itemId);
        if (entity == null || !entity.getCollectionId().equals(collectionId)) {
            throw new ApiTestException("集合项不存在");
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled() ? 1 : 0);
        }
        if (dto.getFolderId() != null) {
            entity.setFolderId(dto.getFolderId());
        }
        updateById(entity);

        ApiDefinitionEntity definition = definitionMapper.selectById(entity.getDefinitionId());
        return buildItemVO(entity, definition);
    }

    /**
     * 删除集合项
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long collectionId, Long itemId) {
        CollectionItemEntity entity = getById(itemId);
        if (entity == null || !entity.getCollectionId().equals(collectionId)) {
            throw new ApiTestException("集合项不存在");
        }
        removeById(itemId);
        log.info("删除集合项: id={}, definitionId={}", itemId, entity.getDefinitionId());
    }

    /**
     * 重排序
     */
    @Transactional(rollbackFor = Exception.class)
    public void reorder(Long collectionId, List<Long> itemIds) {
        for (int i = 0; i < itemIds.size(); i++) {
            CollectionItemEntity entity = getById(itemIds.get(i));
            if (entity != null && entity.getCollectionId().equals(collectionId)) {
                entity.setSortOrder(i);
                updateById(entity);
            }
        }
        log.info("重排序集合项: collectionId={}, itemIds={}", collectionId, itemIds);
    }

    /**
     * 批量添加
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchAdd(Long collectionId, CollectionItemBatchDTO dto, Long userId) {
        // 校验集合是否存在
        CollectionEntity collection = collectionService.getById(collectionId);
        if (collection == null) {
            throw new ApiTestException("集合不存在");
        }

        // 获取当前最大排序序号
        Integer maxSort = getMaxSortOrder(collectionId, dto.getFolderId());

        int sortOffset = 0;
        for (Long definitionId : dto.getDefinitionIds()) {
            // 检查是否已添加
            boolean exists = lambdaQuery()
                    .eq(CollectionItemEntity::getCollectionId, collectionId)
                    .eq(CollectionItemEntity::getDefinitionId, definitionId)
                    .exists();
            if (exists) {
                continue;
            }

            // 校验接口定义是否存在
            ApiDefinitionEntity definition = definitionMapper.selectById(definitionId);
            if (definition == null) {
                continue;
            }

            CollectionItemEntity entity = new CollectionItemEntity();
            entity.setCollectionId(collectionId);
            entity.setFolderId(dto.getFolderId());
            entity.setDefinitionId(definitionId);
            entity.setEnabled(1);
            entity.setSortOrder(maxSort + sortOffset + 1);
            save(entity);
            sortOffset++;
        }

        log.info("批量添加集合项: collectionId={}, count={}", collectionId, dto.getDefinitionIds().size());
    }

    /**
     * 获取当前最大排序序号
     */
    private Integer getMaxSortOrder(Long collectionId, Long folderId) {
        LambdaQueryWrapper<CollectionItemEntity> wrapper = new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getCollectionId, collectionId);
        if (folderId != null) {
            wrapper.eq(CollectionItemEntity::getFolderId, folderId);
        } else {
            wrapper.isNull(CollectionItemEntity::getFolderId);
        }
        wrapper.orderByDesc(CollectionItemEntity::getSortOrder).last("LIMIT 1");
        CollectionItemEntity max = getOne(wrapper, false);
        return max != null && max.getSortOrder() != null ? max.getSortOrder() : 0;
    }

    /**
     * 构建集合项 VO
     */
    private CollectionItemVO buildItemVO(CollectionItemEntity entity, ApiDefinitionEntity definition) {
        CollectionItemVO vo = new CollectionItemVO();
        vo.setId(entity.getId());
        vo.setCollectionId(entity.getCollectionId());
        vo.setFolderId(entity.getFolderId());
        vo.setDefinitionId(entity.getDefinitionId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        vo.setSortOrder(entity.getSortOrder());
        if (definition != null) {
            vo.setMethod(definition.getMethod());
            vo.setPath(definition.getPath());
        }
        return vo;
    }
}