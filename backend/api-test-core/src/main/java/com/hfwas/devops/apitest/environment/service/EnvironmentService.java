package com.hfwas.devops.apitest.environment.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.apitest.environment.dto.EnvironmentCreateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentQueryDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentUpdateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentVariableDTO;
import com.hfwas.devops.apitest.environment.entity.EnvironmentEntity;
import com.hfwas.devops.apitest.environment.entity.EnvironmentVariableEntity;
import com.hfwas.devops.apitest.environment.mapper.EnvironmentMapper;
import com.hfwas.devops.apitest.environment.mapper.EnvironmentVariableMapper;
import com.hfwas.devops.apitest.environment.vo.EnvironmentDetailVO;
import com.hfwas.devops.apitest.environment.vo.EnvironmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 环境变量业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentService extends ServiceImpl<EnvironmentMapper, EnvironmentEntity> {

    private final EnvironmentVariableMapper variableMapper;

    /**
     * 分页查询环境列表
     */
    public IPage<EnvironmentVO> pageQuery(EnvironmentQueryDTO query) {
        Page<EnvironmentEntity> page = new Page<>(query.resolvePageNo(), query.resolvePageSize());

        LambdaQueryWrapper<EnvironmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getProjectId() != null) {
            wrapper.eq(EnvironmentEntity::getProjectId, query.getProjectId());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(EnvironmentEntity::getName, query.getKeyword());
        }
        wrapper.orderByAsc(EnvironmentEntity::getSortOrder);

        IPage<EnvironmentEntity> entityPage = page(page, wrapper);
        // MyBatis-Plus 3.5.10+ 移除了 PaginationInnerInterceptor，需手动分页
        if (entityPage.getTotal() == 0 && !entityPage.getRecords().isEmpty()) {
            entityPage.setTotal(baseMapper.selectCount(wrapper));
        }
        // 无 PaginationInnerInterceptor 时 LIMIT 不生效，手动截取
        long pageSize = page.getSize();
        if (pageSize > 0 && entityPage.getRecords().size() > pageSize) {
            List<EnvironmentEntity> records = entityPage.getRecords().stream()
                    .skip((page.getCurrent() - 1) * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());
            entityPage.setRecords(records);
        }

        return entityPage.convert(entity -> {
            EnvironmentVO vo = new EnvironmentVO();
            vo.setId(entity.getId());
            vo.setProjectId(entity.getProjectId());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setSortOrder(entity.getSortOrder());
            vo.setCreateTime(entity.getCreateTime());
            vo.setUpdateTime(entity.getUpdateTime());

            // 统计变量数量
            Long count = variableMapper.selectCount(
                    new LambdaQueryWrapper<EnvironmentVariableEntity>()
                            .eq(EnvironmentVariableEntity::getEnvironmentId, entity.getId()));
            vo.setVariableCount(count != null ? count.intValue() : 0);

            return vo;
        });
    }

    /**
     * 查询所有环境列表（不分页）
     */
    public List<EnvironmentVO> listAll(Long projectId) {
        List<EnvironmentEntity> entities = lambdaQuery()
                .eq(EnvironmentEntity::getProjectId, projectId)
                .orderByAsc(EnvironmentEntity::getSortOrder)
                .list();

        return entities.stream().map(entity -> {
            EnvironmentVO vo = new EnvironmentVO();
            vo.setId(entity.getId());
            vo.setProjectId(entity.getProjectId());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setSortOrder(entity.getSortOrder());
            vo.setCreateTime(entity.getCreateTime());
            vo.setUpdateTime(entity.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取环境详情（含变量列表）
     */
    public EnvironmentDetailVO getDetail(Long id) {
        EnvironmentEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("环境不存在");
        }

        return buildDetailVO(entity);
    }

    /**
     * 创建环境
     */
    @Transactional(rollbackFor = Exception.class)
    public EnvironmentDetailVO create(EnvironmentCreateDTO dto, Long projectId, Long userId) {
        // 校验环境名唯一性
        boolean exists = lambdaQuery()
                .eq(EnvironmentEntity::getProjectId, projectId)
                .eq(EnvironmentEntity::getName, dto.getName())
                .exists();
        if (exists) {
            throw new ApiTestException("同一项目下已存在同名环境");
        }

        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setProjectId(projectId);
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        save(entity);

        // 保存变量
        if (dto.getVariables() != null && !dto.getVariables().isEmpty()) {
            batchSaveVariables(entity.getId(), dto.getVariables());
        }

        log.info("创建环境: id={}, name={}, projectId={}", entity.getId(), entity.getName(), projectId);
        return buildDetailVO(entity);
    }

    /**
     * 更新环境
     */
    @Transactional(rollbackFor = Exception.class)
    public EnvironmentDetailVO update(Long id, EnvironmentUpdateDTO dto, Long userId) {
        EnvironmentEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("环境不存在");
        }

        if (StrUtil.isNotBlank(dto.getName())) {
            // 校验环境名唯一性（排除自身）
            boolean exists = lambdaQuery()
                    .eq(EnvironmentEntity::getProjectId, entity.getProjectId())
                    .eq(EnvironmentEntity::getName, dto.getName())
                    .ne(EnvironmentEntity::getId, id)
                    .exists();
            if (exists) {
                throw new ApiTestException("同一项目下已存在同名环境");
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

        // 更新变量（先删后插）
        if (dto.getVariables() != null) {
            variableMapper.delete(new LambdaQueryWrapper<EnvironmentVariableEntity>()
                    .eq(EnvironmentVariableEntity::getEnvironmentId, id));
            batchSaveVariables(id, dto.getVariables());
        }

        log.info("更新环境: id={}, name={}", id, entity.getName());
        return buildDetailVO(entity);
    }

    /**
     * 删除环境
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EnvironmentEntity entity = getById(id);
        if (entity == null) {
            throw new ApiTestException("环境不存在");
        }

        // 删除变量
        variableMapper.delete(new LambdaQueryWrapper<EnvironmentVariableEntity>()
                .eq(EnvironmentVariableEntity::getEnvironmentId, id));
        // 删除环境
        removeById(id);

        log.info("删除环境: id={}, name={}", id, entity.getName());
    }

    /**
     * 获取环境变量映射（用于变量渲染）
     */
    public Map<String, String> getVariableMap(Long environmentId) {
        if (environmentId == null) {
            return Collections.emptyMap();
        }

        List<EnvironmentVariableEntity> variables = variableMapper.selectList(
                new LambdaQueryWrapper<EnvironmentVariableEntity>()
                        .eq(EnvironmentVariableEntity::getEnvironmentId, environmentId));

        return variables.stream()
                .collect(Collectors.toMap(EnvironmentVariableEntity::getName, EnvironmentVariableEntity::getValue));
    }

    /**
     * 批量保存变量
     */
    private void batchSaveVariables(Long environmentId, List<EnvironmentVariableDTO> dtos) {
        List<EnvironmentVariableEntity> entities = dtos.stream().map(dto -> {
            EnvironmentVariableEntity entity = new EnvironmentVariableEntity();
            entity.setEnvironmentId(environmentId);
            entity.setName(dto.getName());
            entity.setValue(dto.getValue());
            entity.setDescription(dto.getDescription());
            entity.setIsSecret(Boolean.TRUE.equals(dto.getIsSecret()) ? 1 : 0);
            entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
            return entity;
        }).collect(Collectors.toList());

        variableMapper.insert(entities);
    }

    /**
     * 构建详情 VO
     */
    private EnvironmentDetailVO buildDetailVO(EnvironmentEntity entity) {
        EnvironmentDetailVO vo = new EnvironmentDetailVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        // 查询变量列表
        List<EnvironmentVariableEntity> variables = variableMapper.selectList(
                new LambdaQueryWrapper<EnvironmentVariableEntity>()
                        .eq(EnvironmentVariableEntity::getEnvironmentId, entity.getId())
                        .orderByAsc(EnvironmentVariableEntity::getSortOrder));

        if (variables != null) {
            vo.setVariables(variables.stream().map(v -> {
                EnvironmentDetailVO.EnvironmentVariableItemVO item = new EnvironmentDetailVO.EnvironmentVariableItemVO();
                item.setId(v.getId());
                item.setName(v.getName());
                // 敏感变量返回掩码
                if (v.getIsSecret() != null && v.getIsSecret() == 1 && StrUtil.isNotBlank(v.getValue())) {
                    item.setValue("******");
                } else {
                    item.setValue(v.getValue());
                }
                item.setDescription(v.getDescription());
                item.setIsSecret(v.getIsSecret() != null && v.getIsSecret() == 1);
                item.setSortOrder(v.getSortOrder());
                return item;
            }).collect(Collectors.toList()));
        }

        return vo;
    }
}