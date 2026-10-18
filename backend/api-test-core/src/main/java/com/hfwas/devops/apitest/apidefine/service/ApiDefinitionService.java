package com.hfwas.devops.apitest.apidefine.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.convert.ApiDefinitionConvert;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionParamDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionQueryDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionResponseDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionUpdateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionParamEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionResponseEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionVersionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionDetailVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionParamVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionResponseVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.apitest.common.security.ApiTestAccessGuard;
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
 * 接口定义业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionService extends ServiceImpl<ApiDefinitionMapper, ApiDefinitionEntity> {

    private final ApiDefinitionConvert apiDefinitionConvert;
    private final ApiDefinitionParamService paramService;
    private final ApiDefinitionResponseService responseService;
    private final ApiDefinitionVersionService versionService;
    private final ApiGroupService groupService;
    private final ApiTestAccessGuard accessGuard;

    /**
     * 分页查询接口列表
     */
    public IPage<ApiDefinitionVO> pageQuery(ApiDefinitionQueryDTO query) {
        Page<ApiDefinitionEntity> page = new Page<>(query.resolvePageNo(), query.resolvePageSize());

        LambdaQueryWrapper<ApiDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        // 项目ID：显式指定先校验；未指定时限定在当前租户可见项目内
        if (query.getProjectId() != null) {
            accessGuard.assertProject(query.getProjectId());
            wrapper.eq(ApiDefinitionEntity::getProjectId, query.getProjectId());
        } else {
            List<Long> accessibleProjectIds = accessGuard.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                return page.convert(entity -> apiDefinitionConvert.toVO(entity));
            }
            wrapper.in(ApiDefinitionEntity::getProjectId, accessibleProjectIds);
        }
        // 分组筛选
        if (query.getGroupId() != null) {
            wrapper.eq(ApiDefinitionEntity::getGroupId, query.getGroupId());
        }
        // 关键词模糊搜索（名称 + 路径）
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(ApiDefinitionEntity::getName, query.getKeyword())
                    .or()
                    .like(ApiDefinitionEntity::getPath, query.getKeyword()));
        }
        // 请求方式
        if (StrUtil.isNotBlank(query.getMethod())) {
            wrapper.eq(ApiDefinitionEntity::getMethod, query.getMethod());
        }
        // 状态
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(ApiDefinitionEntity::getStatus, query.getStatus());
        }
        // 标签（交集查询，需所有标签匹配）
        // tags 字段使用 JacksonTypeHandler，直接 like 会把参数序列化为 JSON 字符串（带引号），
        // 导致中文标签匹配不到；这里用 SQLite JSON1 对数组元素做精确匹配。
        if (CollUtil.isNotEmpty(query.getTags())) {
            for (String tag : query.getTags()) {
                wrapper.apply("json_valid(tags) AND EXISTS (SELECT 1 FROM json_each(tags) WHERE value = {0})", tag);
            }
        }

        wrapper.orderByDesc(ApiDefinitionEntity::getUpdateTime);

        IPage<ApiDefinitionEntity> entityPage = page(page, wrapper);
        // MyBatis-Plus 3.5.10+ 移除了 PaginationInnerInterceptor，需手动分页
        if (entityPage.getTotal() == 0 && !entityPage.getRecords().isEmpty()) {
            entityPage.setTotal(baseMapper.selectCount(wrapper));
        }
        // 无 PaginationInnerInterceptor 时 LIMIT 不生效，手动截取
        long pageSize = page.getSize();
        if (pageSize > 0 && entityPage.getRecords().size() > pageSize) {
            List<ApiDefinitionEntity> records = entityPage.getRecords().stream()
                    .skip((page.getCurrent() - 1) * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());
            entityPage.setRecords(records);
        }

        // 转换为 VO
        return entityPage.convert(entity -> {
            ApiDefinitionVO vo = apiDefinitionConvert.toVO(entity);
            // 填充分组名称
            if (entity.getGroupId() != null) {
                try {
                    vo.setGroupName(groupService.getById(entity.getGroupId()).getName());
                } catch (Exception ignored) {
                    // 分组可能已被删除
                }
            }
            return vo;
        });
    }

    /**
     * 创建接口定义
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiDefinitionDetailVO create(ApiDefinitionCreateDTO dto) {
        // 项目访问校验
        accessGuard.assertProject(dto.getProjectId());
        Long userId = accessGuard.requireUserId();

        // 校验路径 + 方法唯一性
        checkPathMethodUnique(null, dto.getProjectId(), dto.getPath(), dto.getMethod());

        // 保存主表
        ApiDefinitionEntity entity = apiDefinitionConvert.toEntity(dto);
        entity.setCreateBy(userId);
        save(entity);

        // 保存参数
        if (CollUtil.isNotEmpty(dto.getParams())) {
            paramService.batchSave(entity.getId(), dto.getParams());
        }

        // 保存响应
        if (CollUtil.isNotEmpty(dto.getResponses())) {
            responseService.batchSave(entity.getId(), dto.getResponses());
        }

        log.info("创建接口定义: id={}, name={}, path={}, method={}",
                entity.getId(), entity.getName(), entity.getPath(), entity.getMethod());

        return buildDetailVO(entity);
    }

    /**
     * 更新接口定义
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiDefinitionDetailVO update(Long id, ApiDefinitionUpdateDTO dto) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());

        // 项目归属不可变：DTO 不提供 projectId，按实体 projectId 校验
        // 校验路径 + 方法唯一性
        checkPathMethodUnique(id, entity.getProjectId(), dto.getPath(), dto.getMethod());

        // 更新主表
        apiDefinitionConvert.updateEntity(dto, entity);
        updateById(entity);

        // 更新参数（先删后插）
        paramService.lambdaUpdate().eq(ApiDefinitionParamEntity::getDefinitionId, id).remove();
        if (CollUtil.isNotEmpty(dto.getParams())) {
            paramService.batchSave(id, dto.getParams());
        }

        // 更新响应（先删后插）
        responseService.lambdaUpdate().eq(ApiDefinitionResponseEntity::getDefinitionId, id).remove();
        if (CollUtil.isNotEmpty(dto.getResponses())) {
            responseService.batchSave(id, dto.getResponses());
        }

        log.info("更新接口定义: id={}, name={}", id, entity.getName());

        return buildDetailVO(entity);
    }

    /**
     * 获取接口详情
     */
    public ApiDefinitionDetailVO getDetail(Long id) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());
        return buildDetailVO(entity);
    }

    /**
     * 删除接口定义
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());

        // 删除参数
        paramService.lambdaUpdate().eq(ApiDefinitionParamEntity::getDefinitionId, id).remove();
        // 删除响应
        responseService.lambdaUpdate().eq(ApiDefinitionResponseEntity::getDefinitionId, id).remove();
        // 删除主表
        removeById(id);

        log.info("删除接口定义: id={}, name={}", id, entity.getName());
    }

    /**
     * 发布接口（草稿→已发布）
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());
        Long userId = accessGuard.requireUserId();
        if (!"DRAFT".equals(entity.getStatus()) && !"PUBLISHED".equals(entity.getStatus())) {
            throw new ApiTestException("当前状态不允许发布");
        }

        // 如果是从草稿发布，创建版本快照
        if ("DRAFT".equals(entity.getStatus())) {
            versionService.createSnapshot(entity, userId);
        }

        entity.setStatus("PUBLISHED");
        updateById(entity);

        log.info("发布接口: id={}, name={}", id, entity.getName());
    }

    /**
     * 废弃接口（已发布→已废弃）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deprecate(Long id) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());
        if (!"PUBLISHED".equals(entity.getStatus())) {
            throw new ApiTestException("仅已发布状态可废弃");
        }

        entity.setStatus("DEPRECATED");
        updateById(entity);

        log.info("废弃接口: id={}, name={}", id, entity.getName());
    }

    /**
     * 恢复编辑（已发布/已废弃→草稿）
     */
    @Transactional(rollbackFor = Exception.class)
    public void revertToDraft(Long id) {
        ApiDefinitionEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(entity.getProjectId());

        entity.setStatus("DRAFT");
        updateById(entity);

        log.info("恢复草稿: id={}, name={}", id, entity.getName());
    }

    /**
     * 校验路径 + 方法唯一性
     */
    private void checkPathMethodUnique(Long excludeId, Long projectId, String path, String method) {
        boolean exists = lambdaQuery()
                .eq(ApiDefinitionEntity::getProjectId, projectId)
                .eq(ApiDefinitionEntity::getPath, path)
                .eq(ApiDefinitionEntity::getMethod, method)
                .ne(excludeId != null, ApiDefinitionEntity::getId, excludeId)
                .exists();
        if (exists) {
            throw new ApiTestException("同一项目下已存在相同路径和请求方式的接口");
        }
    }

    /**
     * 构建详情 VO（含参数和响应）
     */
    private ApiDefinitionDetailVO buildDetailVO(ApiDefinitionEntity entity) {
        ApiDefinitionDetailVO vo = apiDefinitionConvert.toDetailVO(entity);

        // 填充分组名称
        if (entity.getGroupId() != null) {
            try {
                vo.setGroupName(groupService.getById(entity.getGroupId()).getName());
            } catch (Exception ignored) {
            }
        }

        // 查询参数
        List<ApiDefinitionParamEntity> paramEntities = paramService.lambdaQuery()
                .eq(ApiDefinitionParamEntity::getDefinitionId, entity.getId())
                .orderByAsc(ApiDefinitionParamEntity::getSortOrder)
                .list();
        if (CollUtil.isNotEmpty(paramEntities)) {
            vo.setParams(paramEntities.stream().map(p -> {
                ApiDefinitionParamVO pvo = new ApiDefinitionParamVO();
                BeanUtil.copyProperties(p, pvo);
                pvo.setRequired(p.getRequired() != null && p.getRequired() == 1);
                return pvo;
            }).collect(Collectors.toList()));
        }

        // 查询响应
        List<ApiDefinitionResponseEntity> responseEntities = responseService.lambdaQuery()
                .eq(ApiDefinitionResponseEntity::getDefinitionId, entity.getId())
                .list();
        if (CollUtil.isNotEmpty(responseEntities)) {
            vo.setResponses(responseEntities.stream().map(r -> {
                ApiDefinitionResponseVO rvo = new ApiDefinitionResponseVO();
                BeanUtil.copyProperties(r, rvo);
                return rvo;
            }).collect(Collectors.toList()));
        }

        return vo;
    }
}