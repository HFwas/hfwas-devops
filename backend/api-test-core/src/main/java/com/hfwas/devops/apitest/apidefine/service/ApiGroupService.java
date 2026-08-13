package com.hfwas.devops.apitest.apidefine.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.convert.ApiGroupConvert;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupUpdateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiGroupMapper;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分组业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiGroupService extends ServiceImpl<ApiGroupMapper, ApiGroupEntity> {

    private final ApiGroupConvert apiGroupConvert;
    private final ApiDefinitionService apiDefinitionService;

    /**
     * 创建分组
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiGroupVO create(ApiGroupCreateDTO dto, Long userId) {
        // 校验同名分组
        boolean exists = lambdaQuery()
                .eq(ApiGroupEntity::getProjectId, dto.getProjectId())
                .eq(ApiGroupEntity::getName, dto.getName())
                .eq(dto.getParentId() != null, ApiGroupEntity::getParentId, dto.getParentId())
                .exists();
        if (exists) {
            throw new ApiTestException("同级下已存在同名分组");
        }

        ApiGroupEntity entity = apiGroupConvert.toEntity(dto);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        save(entity);

        log.info("创建分组: id={}, name={}, projectId={}", entity.getId(), entity.getName(), entity.getProjectId());
        return apiGroupConvert.toVO(entity);
    }

    /**
     * 更新分组
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiGroupVO update(Long id, ApiGroupUpdateDTO dto, Long userId) {
        ApiGroupEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("分组不存在");
        }

        // 校验同名
        boolean exists = lambdaQuery()
                .eq(ApiGroupEntity::getProjectId, entity.getProjectId())
                .eq(ApiGroupEntity::getName, dto.getName())
                .eq(entity.getParentId() != null, ApiGroupEntity::getParentId, entity.getParentId())
                .ne(ApiGroupEntity::getId, id)
                .exists();
        if (exists) {
            throw new ApiTestException("同级下已存在同名分组");
        }

        entity.setName(dto.getName());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : entity.getSortOrder());
        entity.setDescription(dto.getDescription());
        updateById(entity);

        return apiGroupConvert.toVO(entity);
    }

    /**
     * 删除分组
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ApiGroupEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("分组不存在");
        }

        // 检查是否有子分组
        boolean hasChildren = lambdaQuery().eq(ApiGroupEntity::getParentId, id).exists();
        if (hasChildren) {
            throw new ApiTestException("请先删除子分组");
        }

        // 检查分组下是否有接口
        boolean hasApis = apiDefinitionService.lambdaQuery()
                .eq(com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity::getGroupId, id)
                .exists();
        if (hasApis) {
            throw new ApiTestException("分组下存在接口，请先移除接口");
        }

        removeById(id);
        log.info("删除分组: id={}, name={}", id, entity.getName());
    }

    /**
     * 获取分组树
     */
    public List<ApiGroupVO> getGroupTree(Long projectId) {
        List<ApiGroupEntity> allGroups = lambdaQuery()
                .eq(ApiGroupEntity::getProjectId, projectId)
                .orderByAsc(ApiGroupEntity::getSortOrder)
                .list();

        if (CollUtil.isEmpty(allGroups)) {
            return new ArrayList<>();
        }

        // 统计每个分组下的接口数量
        Map<Long, Long> apiCountMap = apiDefinitionService.lambdaQuery()
                .eq(com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity::getProjectId, projectId)
                .select(com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity::getGroupId)
                .list()
                .stream()
                .filter(a -> a.getGroupId() != null)
                .collect(Collectors.groupingBy(
                        com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity::getGroupId,
                        Collectors.counting()
                ));

        // 构建树
        List<ApiGroupVO> tree = new ArrayList<>();
        Map<Long, List<ApiGroupEntity>> parentMap = allGroups.stream()
                .collect(Collectors.groupingBy(
                        g -> g.getParentId() != null ? g.getParentId() : 0L));

        buildTree(null, parentMap, apiCountMap, tree);

        // 排序
        tree.sort(Comparator.comparingInt(ApiGroupVO::getSortOrder));
        return tree;
    }

    /**
     * 递归构建分组树
     */
    private void buildTree(Long parentId, Map<Long, List<ApiGroupEntity>> parentMap,
                           Map<Long, Long> apiCountMap, List<ApiGroupVO> result) {
        List<ApiGroupEntity> children = parentMap.get(parentId != null ? parentId : 0L);
        if (CollUtil.isEmpty(children)) {
            return;
        }

        for (ApiGroupEntity child : children) {
            ApiGroupVO vo = apiGroupConvert.toTreeVO(child);
            vo.setApiCount(apiCountMap.getOrDefault(child.getId(), 0L).intValue());
            vo.setChildren(new ArrayList<>());
            buildTree(child.getId(), parentMap, apiCountMap, vo.getChildren());
            result.add(vo);
        }
    }

    /**
     * 获取分组详情
     */
    public ApiGroupVO getDetail(Long id) {
        ApiGroupEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("分组不存在");
        }
        return apiGroupConvert.toVO(entity);
    }
}