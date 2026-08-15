package com.hfwas.devops.apitest.collection.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionUpdateDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionFolderEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionItemEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionRunEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionFolderMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionItemMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionRunMapper;
import com.hfwas.devops.apitest.collection.vo.CollectionDetailVO;
import com.hfwas.devops.apitest.collection.vo.CollectionFolderVO;
import com.hfwas.devops.apitest.collection.vo.CollectionItemVO;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 集合业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService extends ServiceImpl<CollectionMapper, CollectionEntity> {

    private final CollectionFolderMapper folderMapper;
    private final CollectionItemMapper itemMapper;
    private final CollectionRunMapper runMapper;
    private final ApiDefinitionMapper definitionMapper;

    /**
     * 分页查询集合列表
     */
    public IPage<CollectionVO> pageQuery(Long projectId, String keyword, Integer pageNo, Integer pageSize) {
        Page<CollectionEntity> page = new Page<>(
                pageNo != null && pageNo > 0 ? pageNo : 1,
                pageSize != null && pageSize > 0 ? pageSize : 20);

        LambdaQueryWrapper<CollectionEntity> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(CollectionEntity::getProjectId, projectId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(CollectionEntity::getName, keyword);
        }
        wrapper.orderByAsc(CollectionEntity::getSortOrder);

        IPage<CollectionEntity> entityPage = page(page, wrapper);
        // MyBatis-Plus 3.5.10+ 移除了 PaginationInnerInterceptor，需手动分页
        if (entityPage.getTotal() == 0 && !entityPage.getRecords().isEmpty()) {
            entityPage.setTotal(baseMapper.selectCount(wrapper));
        }
        // 无 PaginationInnerInterceptor 时 LIMIT 不生效，手动截取
        long currentPageSize = page.getSize();
        if (currentPageSize > 0 && entityPage.getRecords().size() > currentPageSize) {
            List<CollectionEntity> records = entityPage.getRecords().stream()
                    .skip((page.getCurrent() - 1) * currentPageSize)
                    .limit(currentPageSize)
                    .collect(Collectors.toList());
            entityPage.setRecords(records);
        }

        return entityPage.convert(entity -> {
            CollectionVO vo = new CollectionVO();
            vo.setId(entity.getId());
            vo.setProjectId(entity.getProjectId());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setSortOrder(entity.getSortOrder());
            vo.setCreateTime(entity.getCreateTime());
            vo.setUpdateTime(entity.getUpdateTime());

            // 统计文件夹数量
            Long folderCount = folderMapper.selectCount(
                    new LambdaQueryWrapper<CollectionFolderEntity>()
                            .eq(CollectionFolderEntity::getCollectionId, entity.getId()));
            vo.setFolderCount(folderCount != null ? folderCount.intValue() : 0);

            // 统计集合项数量
            Long itemCount = itemMapper.selectCount(
                    new LambdaQueryWrapper<CollectionItemEntity>()
                            .eq(CollectionItemEntity::getCollectionId, entity.getId()));
            vo.setItemCount(itemCount != null ? itemCount.intValue() : 0);

            return vo;
        });
    }

    /**
     * 获取集合详情（含树形结构）
     */
    public CollectionDetailVO getDetail(Long id) {
        CollectionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("集合不存在");
        }

        CollectionDetailVO vo = new CollectionDetailVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());

        // 查询所有文件夹
        List<CollectionFolderEntity> allFolders = folderMapper.selectList(
                new LambdaQueryWrapper<CollectionFolderEntity>()
                        .eq(CollectionFolderEntity::getCollectionId, id)
                        .orderByAsc(CollectionFolderEntity::getSortOrder));

        // 查询所有集合项
        List<CollectionItemEntity> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<CollectionItemEntity>()
                        .eq(CollectionItemEntity::getCollectionId, id)
                        .orderByAsc(CollectionItemEntity::getSortOrder));

        // 获取接口定义信息（path/method）
        Map<Long, ApiDefinitionEntity> definitionMap = getDefinitionMap(allItems);

        // 构建文件夹树（根级文件夹）
        List<CollectionFolderVO> rootFolders = buildFolderTree(allFolders, allItems, definitionMap, null);
        vo.setFolders(rootFolders);

        // 根级集合项（未归入文件夹的项）
        List<Long> folderIds = allFolders.stream().map(CollectionFolderEntity::getId).collect(Collectors.toList());
        List<CollectionItemVO> rootItems = allItems.stream()
                .filter(item -> item.getFolderId() == null || !folderIds.contains(item.getFolderId()))
                .map(item -> buildItemVO(item, definitionMap))
                .collect(Collectors.toList());
        vo.setItems(rootItems);

        return vo;
    }

    /**
     * 创建集合
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionVO create(CollectionCreateDTO dto, Long projectId, Long userId) {
        // 校验集合名唯一性
        boolean exists = lambdaQuery()
                .eq(CollectionEntity::getProjectId, projectId)
                .eq(CollectionEntity::getName, dto.getName())
                .exists();
        if (exists) {
            throw new ApiTestException("同一项目下已存在同名集合");
        }

        CollectionEntity entity = new CollectionEntity();
        entity.setProjectId(projectId);
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        save(entity);

        log.info("创建集合: id={}, name={}, projectId={}", entity.getId(), entity.getName(), projectId);

        CollectionVO vo = new CollectionVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setFolderCount(0);
        vo.setItemCount(0);
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 更新集合
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionVO update(Long id, CollectionUpdateDTO dto, Long userId) {
        CollectionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("集合不存在");
        }

        if (StrUtil.isNotBlank(dto.getName())) {
            // 校验名称唯一性（排除自身）
            boolean exists = lambdaQuery()
                    .eq(CollectionEntity::getProjectId, entity.getProjectId())
                    .eq(CollectionEntity::getName, dto.getName())
                    .ne(CollectionEntity::getId, id)
                    .exists();
            if (exists) {
                throw new ApiTestException("同一项目下已存在同名集合");
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

        log.info("更新集合: id={}, name={}", id, entity.getName());

        CollectionVO vo = new CollectionVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 删除集合（逻辑删除，同时删除关联数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CollectionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("集合不存在");
        }

        // 逻辑删除文件夹
        folderMapper.delete(new LambdaQueryWrapper<CollectionFolderEntity>()
                .eq(CollectionFolderEntity::getCollectionId, id));
        // 逻辑删除集合项
        itemMapper.delete(new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getCollectionId, id));
        // 逻辑删除执行记录
        runMapper.delete(new LambdaQueryWrapper<CollectionRunEntity>()
                .eq(CollectionRunEntity::getCollectionId, id));
        // 逻辑删除集合
        removeById(id);

        log.info("删除集合: id={}, name={}", id, entity.getName());
    }

    /**
     * 构建文件夹树
     */
    private List<CollectionFolderVO> buildFolderTree(List<CollectionFolderEntity> allFolders,
                                                      List<CollectionItemEntity> allItems,
                                                      Map<Long, ApiDefinitionEntity> definitionMap,
                                                      Long parentId) {
        List<CollectionFolderVO> result = new ArrayList<>();
        for (CollectionFolderEntity folder : allFolders) {
            if ((parentId == null && folder.getParentId() == null)
                    || (parentId != null && parentId.equals(folder.getParentId()))) {
                CollectionFolderVO vo = new CollectionFolderVO();
                vo.setId(folder.getId());
                vo.setCollectionId(folder.getCollectionId());
                vo.setParentId(folder.getParentId());
                vo.setName(folder.getName());
                vo.setDescription(folder.getDescription());
                vo.setSortOrder(folder.getSortOrder());

                // 递归构建子文件夹
                vo.setChildren(buildFolderTree(allFolders, allItems, definitionMap, folder.getId()));

                // 当前文件夹下的集合项
                List<CollectionItemVO> folderItems = allItems.stream()
                        .filter(item -> folder.getId().equals(item.getFolderId()))
                        .map(item -> buildItemVO(item, definitionMap))
                        .collect(Collectors.toList());
                vo.setItems(folderItems);

                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 构建集合项 VO
     */
    private CollectionItemVO buildItemVO(CollectionItemEntity item, Map<Long, ApiDefinitionEntity> definitionMap) {
        CollectionItemVO vo = new CollectionItemVO();
        vo.setId(item.getId());
        vo.setCollectionId(item.getCollectionId());
        vo.setFolderId(item.getFolderId());
        vo.setDefinitionId(item.getDefinitionId());
        vo.setName(item.getName());
        vo.setDescription(item.getDescription());
        vo.setEnabled(item.getEnabled() != null && item.getEnabled() == 1);
        vo.setSortOrder(item.getSortOrder());

        // 填充接口定义信息
        ApiDefinitionEntity def = definitionMap.get(item.getDefinitionId());
        if (def != null) {
            vo.setMethod(def.getMethod());
            vo.setPath(def.getPath());
        }

        return vo;
    }

    /**
     * 获取接口定义映射
     */
    private Map<Long, ApiDefinitionEntity> getDefinitionMap(List<CollectionItemEntity> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> definitionIds = items.stream()
                .map(CollectionItemEntity::getDefinitionId)
                .distinct()
                .collect(Collectors.toList());
        List<ApiDefinitionEntity> definitions = definitionMapper.selectBatchIds(definitionIds);
        return definitions.stream().collect(Collectors.toMap(ApiDefinitionEntity::getId, d -> d));
    }
}