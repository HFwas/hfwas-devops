package com.hfwas.devops.apitest.apidefine.service;

import com.hfwas.devops.apitest.apidefine.dto.ApiDebugAssertionDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExecuteDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExtractDTO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDebugResultVO;
import com.hfwas.devops.apitest.debugger.assertion.AssertionExecutor;
import com.hfwas.devops.apitest.debugger.engine.HttpDebugEngine;
import com.hfwas.devops.apitest.debugger.extract.VariableExtractor;
import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import com.hfwas.devops.apitest.debugger.script.PostResponseScriptExecutor;
import com.hfwas.devops.apitest.debugger.script.PreRequestScriptExecutor;
import com.hfwas.devops.apitest.debugger.script.ScriptSandbox;
import com.hfwas.devops.apitest.debugger.variable.VariableRenderer;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.history.service.DebugHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 接口调试服务
 * <p>
 * 编排完整的调试执行流程：
 * 1. 获取环境变量
 * 2. 变量渲染（替换 {{varName}} 占位符）
 * 3. 执行前置脚本
 * 4. 发送 HTTP 请求
 * 5. 执行后置脚本
 * 6. 执行断言
 * 7. 提取变量
 * 8. 保存调试历史
 * 9. 返回结果
 *
 * @author hfwas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDebugService {

    private final HttpDebugEngine httpDebugEngine;
    private final VariableRenderer variableRenderer;
    private final PreRequestScriptExecutor preRequestScriptExecutor;
    private final PostResponseScriptExecutor postResponseScriptExecutor;
    private final AssertionExecutor assertionExecutor;
    private final VariableExtractor variableExtractor;
    private final EnvironmentService environmentService;
    private final DebugHistoryService debugHistoryService;

    /**
     * 执行接口调试
     *
     * @param dto    调试请求参数
     * @param userId 执行用户ID
     * @return 调试结果
     */
    public ApiDebugResultVO execute(ApiDebugExecuteDTO dto, Long userId) {
        log.info("开始执行调试: url={}, method={}, envId={}", dto.getUrl(), dto.getMethod(), dto.getEnvironmentId());

        // ===== 1. 获取环境变量 =====
        Map<String, String> environmentVariables = getEnvironmentVariables(dto.getEnvironmentId());

        // ===== 2. 变量渲染 =====
        RenderedRequest rendered = renderVariables(dto, environmentVariables);
        // 保留原始方法
        rendered.method = dto.getMethod();

        // ===== 3. 执行前置脚本 =====
        ScriptSandbox.PreRequestResult preResult = executePreScript(
                dto.getPreRequestScript(), rendered, environmentVariables);

        // ===== 4. 构建请求并发送 HTTP 请求 =====
        DebugRequest debugRequest = buildDebugRequest(preResult);
        DebugResult debugResult = httpDebugEngine.execute(debugRequest);

        // 记录脚本日志
        List<String> preRequestLogs = preResult != null ? preResult.getLogs() : Collections.emptyList();
        debugResult.setPreRequestLogs(preRequestLogs);

        // ===== 5. 执行后置脚本 =====
        ScriptSandbox.PostResponseResult postResult = executePostScript(
                dto.getPostResponseScript(), debugResult, getEffectiveVariables(environmentVariables, preResult));
        List<String> postResponseLogs = postResult != null ? postResult.getLogs() : Collections.emptyList();
        debugResult.setPostResponseLogs(postResponseLogs);

        // 合并后置脚本修改的环境变量
        if (postResult != null && postResult.getEnvironmentVariables() != null) {
            environmentVariables.putAll(postResult.getEnvironmentVariables());
        }

        // ===== 6. 执行断言 =====
        List<Map<String, Object>> assertionResults = executeAssertions(
                dto.getAssertions(), debugResult);
        debugResult.setAssertionResults(assertionResults);
        debugResult.setAllAssertionsPassed(assertionResults.stream().allMatch(r -> Boolean.TRUE.equals(r.get("passed"))));

        // ===== 7. 提取变量 =====
        Map<String, String> extractedVariables = extractVariables(
                dto.getExtracts(), debugResult);
        debugResult.setExtractedVariables(extractedVariables);

        // ===== 8. 保存调试历史 =====
        Long historyId = debugHistoryService.save(debugResult, dto, userId);

        // ===== 9. 转换为 VO 返回 =====
        return toResultVO(debugResult, historyId);
    }

    /**
     * 获取环境变量
     */
    private Map<String, String> getEnvironmentVariables(Long environmentId) {
        if (environmentId == null) {
            return Collections.emptyMap();
        }
        Map<String, String> variables = environmentService.getVariableMap(environmentId);
        log.debug("获取环境变量: envId={}, count={}", environmentId, variables.size());
        return variables;
    }

    /**
     * 渲染请求参数中的变量占位符
     */
    private RenderedRequest renderVariables(ApiDebugExecuteDTO dto, Map<String, String> environmentVariables) {
        RenderedRequest rendered = new RenderedRequest();

        // 渲染 URL
        rendered.url = variableRenderer.render(dto.getUrl(), environmentVariables);

        // 渲染请求头
        if (dto.getHeaders() != null) {
            rendered.headers = variableRenderer.renderMap(dto.getHeaders(), environmentVariables);
        } else {
            rendered.headers = Collections.emptyMap();
        }

        // 渲染 Query 参数
        if (dto.getQueryParams() != null) {
            rendered.queryParams = variableRenderer.renderMap(dto.getQueryParams(), environmentVariables);
        } else {
            rendered.queryParams = Collections.emptyMap();
        }

        // 渲染请求体
        if (dto.getBody() != null) {
            rendered.body = variableRenderer.render(dto.getBody(), environmentVariables);
        } else {
            rendered.body = null;
        }

        log.debug("变量渲染完成: url={}", rendered.url);
        return rendered;
    }

    /**
     * 执行前置脚本
     */
    private ScriptSandbox.PreRequestResult executePreScript(
            String script, RenderedRequest rendered, Map<String, String> environmentVariables) {

        if (script == null || script.isBlank()) {
            ScriptSandbox.PreRequestResult emptyResult = new ScriptSandbox.PreRequestResult();
            emptyResult.setUrl(rendered.url);
            emptyResult.setMethod(rendered.method);
            emptyResult.setHeaders(rendered.headers);
            emptyResult.setBody(rendered.body);
            emptyResult.setEnvironmentVariables(environmentVariables);
            emptyResult.setLogs(Collections.emptyList());
            return emptyResult;
        }

        log.debug("执行前置脚本: scriptLength={}", script.length());
        return preRequestScriptExecutor.execute(
                script,
                rendered.url,
                rendered.method,
                rendered.headers,
                rendered.body,
                environmentVariables
        );
    }

    /**
     * 执行后置脚本
     */
    private ScriptSandbox.PostResponseResult executePostScript(
            String script, DebugResult debugResult, Map<String, String> environmentVariables) {

        if (script == null || script.isBlank()) {
            ScriptSandbox.PostResponseResult emptyResult = new ScriptSandbox.PostResponseResult();
            emptyResult.setEnvironmentVariables(environmentVariables);
            emptyResult.setLogs(Collections.emptyList());
            return emptyResult;
        }

        log.debug("执行后置脚本: scriptLength={}", script.length());
        return postResponseScriptExecutor.execute(
                script,
                debugResult.getResponse() != null ? debugResult.getResponse().getStatusCode() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getHeaders() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getBody() : null,
                environmentVariables
        );
    }

    /**
     * 获取有效的环境变量（合并前置脚本修改）
     */
    private Map<String, String> getEffectiveVariables(
            Map<String, String> environmentVariables,
            ScriptSandbox.PreRequestResult preResult) {

        if (preResult != null && preResult.getEnvironmentVariables() != null) {
            Map<String, String> effective = new java.util.HashMap<>(environmentVariables);
            effective.putAll(preResult.getEnvironmentVariables());
            return effective;
        }
        return environmentVariables;
    }

    /**
     * 执行断言
     */
    private List<Map<String, Object>> executeAssertions(
            List<ApiDebugAssertionDTO> assertions, DebugResult debugResult) {

        if (assertions == null || assertions.isEmpty()) {
            return Collections.emptyList();
        }

        // 转换断言规则
        List<AssertionExecutor.AssertionRule> rules = assertions.stream()
                .map(this::toAssertionRule)
                .collect(java.util.stream.Collectors.toList());

        // 执行断言
        return assertionExecutor.execute(
                rules,
                debugResult.getResponse() != null ? debugResult.getResponse().getStatusCode() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getHeaders() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getBody() : null,
                debugResult.getDurationMs()
        );
    }

    /**
     * 提取变量
     */
    private Map<String, String> extractVariables(
            List<ApiDebugExtractDTO> extracts, DebugResult debugResult) {

        if (extracts == null || extracts.isEmpty()) {
            return Collections.emptyMap();
        }

        // 转换提取规则
        List<VariableExtractor.ExtractRule> rules = extracts.stream()
                .map(this::toExtractRule)
                .collect(java.util.stream.Collectors.toList());

        // 提取变量
        return variableExtractor.extract(
                rules,
                debugResult.getResponse() != null ? debugResult.getResponse().getStatusCode() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getHeaders() : null,
                debugResult.getResponse() != null ? debugResult.getResponse().getBody() : null
        );
    }

    /**
     * 构建 DebugRequest
     */
    private DebugRequest buildDebugRequest(ScriptSandbox.PreRequestResult preResult) {
        DebugRequest request = new DebugRequest();
        request.setUrl(preResult.getUrl());
        request.setMethod(preResult.getMethod());
        request.setHeaders(preResult.getHeaders());
        request.setQueryParams(null); // Query 参数已渲染到 URL 中
        request.setBody(preResult.getBody());
        // Content-Type 优先从请求头中获取，否则为 null（引擎默认）
        request.setContentType(preResult.getHeaders() != null
                ? preResult.getHeaders().get("Content-Type") : null);
        // 超时和重定向使用默认值
        request.setTimeoutMs(30000L);
        request.setFollowRedirects(true);
        return request;
    }

    /**
     * 将 ApiDebugAssertionDTO 转换为 AssertionRule
     */
    private AssertionExecutor.AssertionRule toAssertionRule(ApiDebugAssertionDTO dto) {
        return new AssertionExecutor.AssertionRule(
                dto.getName(),
                dto.getSource(),
                dto.getCompareType(),
                dto.getExpression(),
                dto.getExpectedValue()
        );
    }

    /**
     * 将 ApiDebugExtractDTO 转换为 ExtractRule
     */
    private VariableExtractor.ExtractRule toExtractRule(ApiDebugExtractDTO dto) {
        return new VariableExtractor.ExtractRule(
                dto.getVariableName(),
                dto.getExpression(),
                dto.getSource()
        );
    }

    /**
     * 将 DebugResult 转换为 ApiDebugResultVO
     */
    private ApiDebugResultVO toResultVO(DebugResult debugResult, Long historyId) {
        ApiDebugResultVO vo = new ApiDebugResultVO();

        // 历史ID
        vo.setHistoryId(historyId);

        // 请求报文
        if (debugResult.getRequest() != null) {
            vo.setRequestUrl(debugResult.getRequest().getUrl());
            vo.setRequestMethod(debugResult.getRequest().getMethod());
            vo.setRequestHeaders(debugResult.getRequest().getHeaders());
            vo.setRequestQuery(debugResult.getRequest().getQueryParams());
            vo.setRequestBody(debugResult.getRequest().getBody());
            vo.setRequestContentType(debugResult.getRequest().getContentType());
        }

        // 响应报文
        if (debugResult.getResponse() != null) {
            vo.setResponseStatusCode(debugResult.getResponse().getStatusCode());
            vo.setResponseHeaders(debugResult.getResponse().getHeaders());
            vo.setResponseBody(debugResult.getResponse().getBody());
            vo.setResponseContentType(debugResult.getResponse().getContentType());
            vo.setResponseSize(debugResult.getResponse().getResponseSize());
        }

        // 调试信息
        vo.setDurationMs(debugResult.getDurationMs());
        vo.setStatus(debugResult.getStatus());
        vo.setErrorMessage(debugResult.getErrorMessage());

        // 脚本日志
        vo.setPreRequestLogs(debugResult.getPreRequestLogs());
        vo.setPostResponseLogs(debugResult.getPostResponseLogs());

        // 断言结果
        vo.setAssertionResults(debugResult.getAssertionResults());
        vo.setAllAssertionsPassed(debugResult.getAllAssertionsPassed());

        // 提取变量
        vo.setExtractedVariables(debugResult.getExtractedVariables());

        return vo;
    }

    /**
     * 渲染后的请求参数（内部使用）
     */
    private static class RenderedRequest {
        private String url;
        private String method;
        private Map<String, String> headers;
        private Map<String, String> queryParams;
        private String body;
    }
}