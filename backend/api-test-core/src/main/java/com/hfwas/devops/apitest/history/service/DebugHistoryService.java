package com.hfwas.devops.apitest.history.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExecuteDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.service.ApiDefinitionService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDebugHistoryVO;
import com.hfwas.devops.apitest.common.enums.DebugStatusEnum;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.apitest.common.security.ApiTestAccessGuard;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import com.hfwas.devops.apitest.history.dto.DebugHistoryQueryDTO;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import com.hfwas.devops.apitest.history.mapper.DebugHistoryMapper;
import com.hfwas.devops.apitest.history.vo.DebugHistoryDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调试历史业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebugHistoryService extends ServiceImpl<DebugHistoryMapper, DebugHistoryEntity> {

    private final ApiDefinitionService apiDefinitionService;
    private final ApiTestAccessGuard accessGuard;

    /**
     * 分页查询调试历史
     */
    public IPage<ApiDebugHistoryVO> pageQuery(DebugHistoryQueryDTO query) {
        Page<DebugHistoryEntity> page = new Page<>(query.resolvePageNo(), query.resolvePageSize());

        LambdaQueryWrapper<DebugHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getProjectId() != null) {
            accessGuard.assertProject(query.getProjectId());
            wrapper.eq(DebugHistoryEntity::getProjectId, query.getProjectId());
        } else {
            List<Long> accessibleProjectIds = accessGuard.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                return page.convert(this::toHistoryVO);
            }
            wrapper.in(DebugHistoryEntity::getProjectId, accessibleProjectIds);
        }
        if (query.getDefinitionId() != null) {
            wrapper.eq(DebugHistoryEntity::getDefinitionId, query.getDefinitionId());
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(DebugHistoryEntity::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            // OR 条件必须包裹，避免突破项目过滤
            wrapper.and(w -> w.like(DebugHistoryEntity::getName, query.getKeyword())
                    .or()
                    .like(DebugHistoryEntity::getRequestUrl, query.getKeyword()));
        }
        wrapper.orderByDesc(DebugHistoryEntity::getCreateTime);

        IPage<DebugHistoryEntity> entityPage = page(page, wrapper);
        // MyBatis-Plus 3.5.10+ 移除了 PaginationInnerInterceptor，需手动补 total
        if (entityPage.getTotal() == 0 && !entityPage.getRecords().isEmpty()) {
            entityPage.setTotal(baseMapper.selectCount(wrapper));
        }

        return entityPage.convert(this::toHistoryVO);
    }

    /**
     * 获取调试历史详情
     */
    public DebugHistoryDetailVO getDetail(Long id) {
        DebugHistoryEntity entity = getById(id);
        if (entity == null) {
            return null;
        }
        accessGuard.assertProject(entity.getProjectId());
        return toDetailVO(entity);
    }

    /**
     * 查询某接口的调试历史列表
     */
    public List<ApiDebugHistoryVO> getByDefinitionId(Long definitionId, int limit) {
        ApiDefinitionEntity definition = apiDefinitionService.getById(definitionId);
        if (definition == null) {
            throw new ApiTestException("接口不存在");
        }
        accessGuard.assertProject(definition.getProjectId());

        LambdaQueryWrapper<DebugHistoryEntity> wrapper = new LambdaQueryWrapper<DebugHistoryEntity>()
                .eq(DebugHistoryEntity::getDefinitionId, definitionId)
                .orderByDesc(DebugHistoryEntity::getCreateTime)
                .last("LIMIT " + limit);

        return list(wrapper).stream().map(this::toHistoryVO).collect(Collectors.toList());
    }

    /**
     * 删除调试历史
     */
    public void delete(Long id) {
        DebugHistoryEntity entity = getById(id);
        if (entity != null) {
            accessGuard.assertProject(entity.getProjectId());
        }
        removeById(id);
        log.info("删除调试历史: id={}", id);
    }

    /**
     * 批量删除调试历史
     */
    public void deleteBatch(List<Long> ids) {
        // 先整体校验，任一记录越权则整批拒绝，避免部分删除
        for (Long id : ids) {
            DebugHistoryEntity entity = getById(id);
            if (entity != null) {
                accessGuard.assertProject(entity.getProjectId());
            }
        }
        removeBatchByIds(ids);
        log.info("批量删除调试历史: count={}", ids.size());
    }

    /**
     * 保存调试历史记录
     *
     * @param result 调试结果
     * @param dto    调试请求参数
     * @return 历史记录ID
     */
    public Long save(DebugResult result, ApiDebugExecuteDTO dto) {
        DebugHistoryEntity entity = new DebugHistoryEntity();

        // 基本信息
        entity.setProjectId(dto.getProjectId());
        entity.setDefinitionId(dto.getDefinitionId());
        entity.setEnvironmentId(dto.getEnvironmentId());

        // 自动生成名称：方法 + URL
        String method = result.getRequest() != null ? result.getRequest().getMethod() : dto.getMethod();
        String url = result.getRequest() != null ? result.getRequest().getUrl() : dto.getUrl();
        entity.setName(method + " " + truncateUrl(url));

        // 请求报文
        entity.setRequestUrl(url);
        entity.setRequestMethod(method);
        if (result.getRequest() != null) {
            entity.setRequestHeaders(result.getRequest().getHeaders());
            entity.setRequestQuery(result.getRequest().getQueryParams());
            entity.setRequestBody(result.getRequest().getBody());
            entity.setRequestContentType(result.getRequest().getContentType());
        }

        // 响应报文
        if (result.getResponse() != null) {
            entity.setResponseStatusCode(result.getResponse().getStatusCode());
            entity.setResponseHeaders(result.getResponse().getHeaders());
            entity.setResponseBody(result.getResponse().getBody());
            entity.setResponseContentType(result.getResponse().getContentType());
            entity.setResponseSize(result.getResponse().getResponseSize());
        }

        // 调试信息
        entity.setDurationMs(result.getDurationMs());
        entity.setStatus(result.getStatus() != null ? result.getStatus() : DebugStatusEnum.ERROR.getCode());
        entity.setErrorMessage(result.getErrorMessage());

        // 断言结果
        entity.setAssertionResults(result.getAssertionResults());
        if (result.getAllAssertionsPassed() != null) {
            entity.setAllAssertionsPassed(result.getAllAssertionsPassed() ? 1 : 0);
        }

        // 提取变量
        entity.setExtractedVariables(result.getExtractedVariables());

        // 审计：身份取当前认证上下文
        entity.setCreateBy(accessGuard.requireUserId());

        // 保存
        save(entity);
        log.info("保存调试历史: id={}, name={}, status={}, duration={}ms",
                entity.getId(), entity.getName(), entity.getStatus(), entity.getDurationMs());

        return entity.getId();
    }

    /**
     * 截断 URL 用于显示名称（最长60字符）
     */
    private String truncateUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() > 60) {
            return url.substring(0, 57) + "...";
        }
        return url;
    }

    /**
     * 转换为历史列表 VO
     */
    private ApiDebugHistoryVO toHistoryVO(DebugHistoryEntity entity) {
        ApiDebugHistoryVO vo = new ApiDebugHistoryVO();
        vo.setId(entity.getId());
        vo.setDefinitionId(entity.getDefinitionId());
        vo.setEnvironmentId(entity.getEnvironmentId());
        vo.setName(entity.getName());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setResponseStatusCode(entity.getResponseStatusCode());
        vo.setResponseSize(entity.getResponseSize());
        vo.setDurationMs(entity.getDurationMs());
        vo.setStatus(entity.getStatus());
        vo.setAllAssertionsPassed(entity.getAllAssertionsPassed() != null && entity.getAllAssertionsPassed() == 1);
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 转换为详情 VO
     */
    private DebugHistoryDetailVO toDetailVO(DebugHistoryEntity entity) {
        DebugHistoryDetailVO vo = new DebugHistoryDetailVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setDefinitionId(entity.getDefinitionId());
        vo.setEnvironmentId(entity.getEnvironmentId());
        vo.setName(entity.getName());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setRequestHeaders(entity.getRequestHeaders());
        vo.setRequestQuery(entity.getRequestQuery());
        vo.setRequestBody(entity.getRequestBody());
        vo.setRequestContentType(entity.getRequestContentType());
        vo.setResponseStatusCode(entity.getResponseStatusCode());
        vo.setResponseHeaders(entity.getResponseHeaders());
        vo.setResponseBody(entity.getResponseBody());
        vo.setResponseContentType(entity.getResponseContentType());
        vo.setResponseSize(entity.getResponseSize());
        vo.setDurationMs(entity.getDurationMs());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setAssertionResults(entity.getAssertionResults());
        vo.setAllAssertionsPassed(entity.getAllAssertionsPassed() != null && entity.getAllAssertionsPassed() == 1);
        vo.setExtractedVariables(entity.getExtractedVariables());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}