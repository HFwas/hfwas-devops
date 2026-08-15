package com.hfwas.devops.apitest.collection.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionParamEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionParamMapper;
import com.hfwas.devops.apitest.collection.entity.CollectionItemEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionRunEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionRunItemEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionItemMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionRunItemMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionRunMapper;
import com.hfwas.devops.apitest.collection.vo.CollectionRunDetailVO;
import com.hfwas.devops.apitest.collection.vo.CollectionRunItemVO;
import com.hfwas.devops.apitest.collection.vo.CollectionRunVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.apitest.debugger.engine.HttpDebugEngine;
import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 集合执行业务
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionRunService extends ServiceImpl<CollectionRunMapper, CollectionRunEntity> {

    private final CollectionItemMapper itemMapper;
    private final CollectionRunItemMapper runItemMapper;
    private final ApiDefinitionMapper definitionMapper;
    private final ApiDefinitionParamMapper paramMapper;
    private final HttpDebugEngine httpDebugEngine;
    private final EnvironmentService environmentService;

    /**
     * 执行集合
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectionRunVO execute(Long collectionId, Long environmentId, Long userId) {
        // 查询集合内所有启用的集合项
        List<CollectionItemEntity> items = itemMapper.selectList(
                new LambdaQueryWrapper<CollectionItemEntity>()
                        .eq(CollectionItemEntity::getCollectionId, collectionId)
                        .eq(CollectionItemEntity::getEnabled, 1)
                        .orderByAsc(CollectionItemEntity::getSortOrder));

        if (items.isEmpty()) {
            throw new ApiTestException("集合中没有启用的接口");
        }

        // 创建运行记录
        CollectionRunEntity run = new CollectionRunEntity();
        run.setCollectionId(collectionId);
        run.setName("Run - " + DateUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss"));
        run.setStatus("RUNNING");
        run.setTotalCount(items.size());
        run.setPassedCount(0);
        run.setFailedCount(0);
        run.setErrorCount(0);
        run.setDurationMs(0L);
        run.setTriggerMode("MANUAL");
        save(run);

        // 获取环境变量映射
        Map<String, String> envVariables = environmentService.getVariableMap(environmentId);

        // 逐条执行
        int passed = 0, failed = 0, error = 0;
        long totalDuration = 0;
        int sortOrder = 0;

        for (CollectionItemEntity item : items) {
            sortOrder++;
            CollectionRunItemEntity runItem = new CollectionRunItemEntity();
            runItem.setRunId(run.getId());
            runItem.setCollectionItemId(item.getId());
            runItem.setDefinitionId(item.getDefinitionId());
            runItem.setName(item.getName());
            runItem.setSortOrder(sortOrder);

            try {
                // 加载接口定义和参数
                ApiDefinitionEntity definition = definitionMapper.selectById(item.getDefinitionId());
                if (definition == null) {
                    runItem.setStatus("ERROR");
                    runItem.setErrorMessage("接口定义不存在");
                    runItem.setDurationMs(0L);
                    runItemMapper.insert(runItem);
                    error++;
                    continue;
                }

                // 构建请求
                DebugRequest debugRequest = buildRequest(definition, envVariables);

                // 执行请求
                DebugResult debugResult = httpDebugEngine.execute(debugRequest);

                // 填充结果
                runItem.setRequestUrl(debugRequest.getUrl());
                runItem.setRequestMethod(debugRequest.getMethod());
                runItem.setDurationMs(debugResult.getDurationMs());
                totalDuration += debugResult.getDurationMs();

                if (debugResult.getResponse() != null) {
                    runItem.setResponseStatusCode(debugResult.getResponse().getStatusCode());
                    runItem.setResponseHeaders(JSONUtil.toJsonStr(debugResult.getResponse().getHeaders()));
                    runItem.setResponseBody(debugResult.getResponse().getBody());
                    runItem.setResponseSize(debugResult.getResponse().getResponseSize());
                }

                if ("SUCCESS".equals(debugResult.getStatus())) {
                    runItem.setStatus("SUCCESS");
                    passed++;
                } else if ("FAILURE".equals(debugResult.getStatus())) {
                    runItem.setStatus("FAILURE");
                    failed++;
                } else {
                    runItem.setStatus("ERROR");
                    runItem.setErrorMessage(debugResult.getErrorMessage());
                    error++;
                }

            } catch (Exception e) {
                log.error("执行集合项失败: itemId={}, definitionId={}", item.getId(), item.getDefinitionId(), e);
                runItem.setStatus("ERROR");
                runItem.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                runItem.setDurationMs(0L);
                error++;
            }

            runItemMapper.insert(runItem);
        }

        // 更新运行记录
        run.setStatus(error > 0 && passed == 0 ? "FAILED" : "COMPLETED");
        run.setPassedCount(passed);
        run.setFailedCount(failed);
        run.setErrorCount(error);
        run.setDurationMs(totalDuration);
        updateById(run);

        log.info("集合执行完成: collectionId={}, runId={}, passed={}, failed={}, error={}",
                collectionId, run.getId(), passed, failed, error);

        return buildRunVO(run);
    }

    /**
     * 查询运行历史
     */
    public IPage<CollectionRunVO> pageQuery(Long collectionId, Integer pageNo, Integer pageSize) {
        Page<CollectionRunEntity> page = new Page<>(
                pageNo != null && pageNo > 0 ? pageNo : 1,
                pageSize != null && pageSize > 0 ? pageSize : 20);

        LambdaQueryWrapper<CollectionRunEntity> wrapper = new LambdaQueryWrapper<CollectionRunEntity>()
                .eq(CollectionRunEntity::getCollectionId, collectionId)
                .orderByDesc(CollectionRunEntity::getCreateTime);

        IPage<CollectionRunEntity> entityPage = page(page, wrapper);
        return entityPage.convert(this::buildRunVO);
    }

    /**
     * 获取运行详情
     */
    public CollectionRunDetailVO getDetail(Long runId) {
        CollectionRunEntity run = getById(runId);
        if (run == null) {
            throw new ApiTestException("运行记录不存在");
        }

        CollectionRunDetailVO vo = new CollectionRunDetailVO();
        vo.setId(run.getId());
        vo.setCollectionId(run.getCollectionId());
        vo.setProjectId(run.getProjectId());
        vo.setEnvironmentId(run.getEnvironmentId());
        vo.setName(run.getName());
        vo.setStatus(run.getStatus());
        vo.setTotalCount(run.getTotalCount());
        vo.setPassedCount(run.getPassedCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setDurationMs(run.getDurationMs());
        vo.setTriggerMode(run.getTriggerMode());

        // 查询执行项结果
        List<CollectionRunItemEntity> items = runItemMapper.selectList(
                new LambdaQueryWrapper<CollectionRunItemEntity>()
                        .eq(CollectionRunItemEntity::getRunId, runId)
                        .orderByAsc(CollectionRunItemEntity::getSortOrder));

        vo.setItems(items.stream().map(this::buildRunItemVO).collect(Collectors.toList()));

        return vo;
    }

    /**
     * 删除运行记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long runId) {
        CollectionRunEntity run = getById(runId);
        if (run == null) {
            throw new ApiTestException("运行记录不存在");
        }

        // 删除执行项结果
        runItemMapper.delete(new LambdaQueryWrapper<CollectionRunItemEntity>()
                .eq(CollectionRunItemEntity::getRunId, runId));
        // 删除运行记录
        removeById(runId);

        log.info("删除运行记录: id={}", runId);
    }

    /**
     * 根据接口定义构建调试请求
     */
    private DebugRequest buildRequest(ApiDefinitionEntity definition, Map<String, String> envVariables) {
        DebugRequest request = new DebugRequest();
        request.setMethod(definition.getMethod());

        // 构建 URL（host + path）
        String host = definition.getHost() != null ? definition.getHost() : "";
        String path = definition.getPath() != null ? definition.getPath() : "";
        String url = host + path;

        // 变量渲染
        url = renderVariables(url, envVariables);
        request.setUrl(url);

        // 构建请求头
        Map<String, String> headers = new HashMap<>();
        if (definition.getContentType() != null) {
            headers.put("Content-Type", definition.getContentType());
        }

        // 加载参数
        List<ApiDefinitionParamEntity> params = paramMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionParamEntity>()
                        .eq(ApiDefinitionParamEntity::getDefinitionId, definition.getId()));

        if (params != null) {
            Map<String, String> queryParams = new HashMap<>();
            for (ApiDefinitionParamEntity param : params) {
                String value = param.getDefaultValue() != null ? param.getDefaultValue() : "";

                switch (param.getParamType()) {
                    case "header":
                        headers.put(param.getName(), renderVariables(value, envVariables));
                        break;
                    case "query":
                        queryParams.put(param.getName(), renderVariables(value, envVariables));
                        break;
                    case "body":
                        request.setBody(renderVariables(value, envVariables));
                        break;
                    default:
                        break;
                }
            }
            request.setQueryParams(queryParams);
        }

        request.setHeaders(headers);

        return request;
    }

    /**
     * 简单变量渲染
     */
    private String renderVariables(String text, Map<String, String> variables) {
        if (text == null || variables == null || variables.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private CollectionRunVO buildRunVO(CollectionRunEntity entity) {
        CollectionRunVO vo = new CollectionRunVO();
        vo.setId(entity.getId());
        vo.setCollectionId(entity.getCollectionId());
        vo.setProjectId(entity.getProjectId());
        vo.setEnvironmentId(entity.getEnvironmentId());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus());
        vo.setTotalCount(entity.getTotalCount());
        vo.setPassedCount(entity.getPassedCount());
        vo.setFailedCount(entity.getFailedCount());
        vo.setErrorCount(entity.getErrorCount());
        vo.setDurationMs(entity.getDurationMs());
        vo.setTriggerMode(entity.getTriggerMode());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private CollectionRunItemVO buildRunItemVO(CollectionRunItemEntity entity) {
        CollectionRunItemVO vo = new CollectionRunItemVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setCollectionItemId(entity.getCollectionItemId());
        vo.setDefinitionId(entity.getDefinitionId());
        vo.setName(entity.getName());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setRequestHeaders(entity.getRequestHeaders());
        vo.setRequestBody(entity.getRequestBody());
        vo.setResponseStatusCode(entity.getResponseStatusCode());
        vo.setResponseHeaders(entity.getResponseHeaders());
        vo.setResponseBody(entity.getResponseBody());
        vo.setResponseSize(entity.getResponseSize());
        vo.setDurationMs(entity.getDurationMs());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setAssertionResults(entity.getAssertionResults());
        vo.setAllAssertionsPassed(entity.getAllAssertionsPassed() != null && entity.getAllAssertionsPassed() == 1);
        vo.setExtractedVariables(entity.getExtractedVariables());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }
}