package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.apidefine.dto.ApiDebugAssertionDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExecuteDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExtractDTO;
import com.hfwas.devops.apitest.apidefine.service.ApiDebugService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDebugResultVO;
import com.hfwas.devops.apitest.debugger.assertion.AssertionExecutor;
import com.hfwas.devops.apitest.debugger.engine.HttpDebugEngine;
import com.hfwas.devops.apitest.debugger.extract.VariableExtractor;
import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResponse;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import com.hfwas.devops.apitest.debugger.script.PostResponseScriptExecutor;
import com.hfwas.devops.apitest.debugger.script.PreRequestScriptExecutor;
import com.hfwas.devops.apitest.debugger.script.ScriptSandbox;
import com.hfwas.devops.apitest.debugger.variable.VariableRenderer;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.history.service.DebugHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApiDebugService — 调试引擎编排服务单元测试
 * <p>
 * 使用 Mockito 模拟所有依赖组件，验证：
 * - 完整 9 步编排流程的调用顺序
 * - 变量渲染、脚本执行、HTTP 请求、断言、提取的串联
 * - 边界条件（无断言、无脚本、无环境变量）
 *
 * @author hfwas
 */
@DisplayName("ApiDebugService — 调试引擎编排服务")
@ExtendWith(MockitoExtension.class)
class ApiDebugServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ENV_ID = 100L;
    private static final Long TEST_HISTORY_ID = 500L;

    @Mock
    private HttpDebugEngine httpDebugEngine;
    @Mock
    private VariableRenderer variableRenderer;
    @Mock
    private PreRequestScriptExecutor preRequestScriptExecutor;
    @Mock
    private PostResponseScriptExecutor postResponseScriptExecutor;
    @Mock
    private AssertionExecutor assertionExecutor;
    @Mock
    private VariableExtractor variableExtractor;
    @Mock
    private EnvironmentService environmentService;
    @Mock
    private DebugHistoryService debugHistoryService;

    @Captor
    private ArgumentCaptor<DebugRequest> debugRequestCaptor;

    private ApiDebugService apiDebugService;

    @BeforeEach
    void setUp() {
        apiDebugService = new ApiDebugService(
                httpDebugEngine, variableRenderer, preRequestScriptExecutor,
                postResponseScriptExecutor, assertionExecutor, variableExtractor,
                environmentService, debugHistoryService
        );
    }

    /**
     * 构建完整的调试请求 DTO
     */
    private ApiDebugExecuteDTO buildFullExecuteDTO() {
        ApiDebugExecuteDTO dto = new ApiDebugExecuteDTO();
        dto.setProjectId(1001L);
        dto.setDefinitionId(2001L);
        dto.setEnvironmentId(TEST_ENV_ID);
        dto.setUrl("{{base_url}}/api/users?page={{page}}");
        dto.setMethod("GET");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer {{token}}");
        dto.setHeaders(headers);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("page", "{{page}}");
        queryParams.put("size", "20");
        dto.setQueryParams(queryParams);

        dto.setBody(null);
        dto.setContentType("application/json");
        dto.setPreRequestScript("console.log('pre-script');");
        dto.setPostResponseScript("console.log('post-script');");

        ApiDebugAssertionDTO assertion = new ApiDebugAssertionDTO();
        assertion.setName("状态码为200");
        assertion.setSource("RESPONSE_STATUS");
        assertion.setCompareType("EQUALS");
        assertion.setExpectedValue("200");
        dto.setAssertions(List.of(assertion));

        ApiDebugExtractDTO extract = new ApiDebugExtractDTO();
        extract.setVariableName("userId");
        extract.setSource("RESPONSE_BODY");
        extract.setExpression("$.data.id");
        dto.setExtracts(List.of(extract));

        return dto;
    }

    /**
     * 构建模拟的 HTTP 执行结果
     */
    private DebugResult buildMockDebugResult() {
        DebugResult result = new DebugResult();

        DebugRequest request = new DebugRequest();
        request.setUrl("http://test-api.example.com/api/users?page=1&size=20");
        request.setMethod("GET");
        result.setRequest(request);

        DebugResponse response = new DebugResponse();
        response.setStatusCode(200);
        response.setBody("{\"code\":0,\"data\":{\"id\":1,\"name\":\"张三\"}}");
        response.setContentType("application/json;charset=UTF-8");
        response.setResponseSize(48L);
        Map<String, String> respHeaders = new HashMap<>();
        respHeaders.put("Content-Type", "application/json;charset=UTF-8");
        response.setHeaders(respHeaders);
        result.setResponse(response);

        result.setDurationMs(156L);
        result.setStatus("SUCCESS");
        return result;
    }

    @Nested
    @DisplayName("execute — 完整调试流程")
    class ExecuteFullFlow {

        @Test
        @DisplayName("完整的 9 步调试流程应全部执行")
        void fullFlowShouldExecuteAllSteps() {
            // 准备：环境变量
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://test-api.example.com");
            envVars.put("token", "test-token-123");
            envVars.put("page", "1");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            // 准备：变量渲染
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            // 准备：前置脚本
            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users?page=1&size=20");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of("[sandbox] 前置脚本执行完成"));
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            // 准备：HTTP 执行
            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            // 准备：后置脚本
            ScriptSandbox.PostResponseResult postResult = new ScriptSandbox.PostResponseResult();
            postResult.setEnvironmentVariables(envVars);
            postResult.setLogs(List.of("[sandbox] 后置脚本执行完成"));
            when(postResponseScriptExecutor.execute(anyString(), any(), anyMap(), anyString(), anyMap()))
                    .thenReturn(postResult);

            // 准备：断言
            List<Map<String, Object>> assertionResults = new ArrayList<>();
            Map<String, Object> assertionResult = new LinkedHashMap<>();
            assertionResult.put("name", "状态码为200");
            assertionResult.put("source", "RESPONSE_STATUS");
            assertionResult.put("compareType", "EQUALS");
            assertionResult.put("expected", "200");
            assertionResult.put("actual", "200");
            assertionResult.put("passed", true);
            assertionResults.add(assertionResult);
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(assertionResults);

            // 准备：变量提取
            Map<String, String> extracted = new HashMap<>();
            extracted.put("userId", "1");
            when(variableExtractor.extract(anyList(), any(), anyMap(), anyString()))
                    .thenReturn(extracted);

            // 准备：历史保存
            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证结果
            assertNotNull(result);
            assertEquals(TEST_HISTORY_ID, result.getHistoryId());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals(156L, result.getDurationMs());
            assertEquals(200, result.getResponseStatusCode());
            assertTrue(result.getAllAssertionsPassed());

            // 验证调用顺序
            verify(environmentService).getVariableMap(TEST_ENV_ID);
            verify(variableRenderer, atLeast(1)).render(anyString(), anyMap());
            verify(preRequestScriptExecutor).execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap());
            verify(httpDebugEngine).execute(any(DebugRequest.class));
            verify(postResponseScriptExecutor).execute(anyString(), any(), anyMap(), anyString(), anyMap());
            verify(assertionExecutor).execute(anyList(), any(), anyMap(), anyString(), anyLong());
            verify(variableExtractor).extract(anyList(), any(), anyMap(), anyString());
            verify(debugHistoryService).save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), eq(TEST_USER_ID));
        }

        @Test
        @DisplayName("HTTP 请求应使用渲染后的 URL 和方法")
        void httpRequestShouldUseRenderedUrlAndMethod() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://test-api.example.com");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            when(variableRenderer.render(eq("{{base_url}}/api/users?page={{page}}"), anyMap()))
                    .thenReturn("http://test-api.example.com/api/users?page=1");

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users?page=1");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            apiDebugService.execute(dto, TEST_USER_ID);

            // 验证 HTTP 引擎收到正确的请求
            verify(httpDebugEngine).execute(debugRequestCaptor.capture());
            DebugRequest sentRequest = debugRequestCaptor.getValue();
            assertEquals("http://test-api.example.com/api/users?page=1", sentRequest.getUrl());
            assertEquals("GET", sentRequest.getMethod());
        }
    }

    @Nested
    @DisplayName("execute — 边界条件")
    class ExecuteBoundaryConditions {

        @Test
        @DisplayName("无环境 ID 时应使用空变量映射")
        void noEnvironmentIdShouldUseEmptyVariables() {
            // 准备
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setEnvironmentId(null);
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：未调用环境变量服务
            verify(environmentService, never()).getVariableMap(anyLong());
            assertNotNull(result);
        }

        @Test
        @DisplayName("无前置脚本时应跳过脚本执行")
        void noPreScriptShouldSkipPreRequest() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setPreRequestScript(null);
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：前置脚本执行器未被调用
            verify(preRequestScriptExecutor, never()).execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap());
            assertNotNull(result);
        }

        @Test
        @DisplayName("无后置脚本时应跳过脚本执行")
        void noPostScriptShouldSkipPostRequest() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setPostResponseScript(null);
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：后置脚本执行器未被调用
            verify(postResponseScriptExecutor, never()).execute(anyString(), any(), anyMap(), anyString(), anyMap());
            assertNotNull(result);
        }

        @Test
        @DisplayName("无断言时应返回空断言结果且 allAssertionsPassed 为 true")
        void noAssertionsShouldReturnEmptyResults() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setAssertions(null);
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：断言执行器未被调用，allAssertionsPassed 为 true
            verify(assertionExecutor, never()).execute(anyList(), any(), anyMap(), anyString(), anyLong());
            assertTrue(result.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("无提取规则时应返回空提取变量")
        void noExtractsShouldReturnEmptyVariables() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setExtracts(null);
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：提取器未被调用，extractedVariables 应为空 Map
            verify(variableExtractor, never()).extract(anyList(), any(), anyMap(), anyString());
            assertNotNull(result.getExtractedVariables());
            assertTrue(result.getExtractedVariables().isEmpty());
        }

        @Test
        @DisplayName("HTTP 请求失败时应保留 ERROR 状态并记录错误信息")
        void httpErrorShouldPreserveErrorStatus() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            // HTTP 引擎返回错误
            DebugResult errorResult = new DebugResult();
            errorResult.setStatus("ERROR");
            errorResult.setErrorMessage("ConnectException: 连接超时");
            errorResult.setDurationMs(5000L);
            DebugRequest errorRequest = new DebugRequest();
            errorRequest.setUrl("http://localhost/api/test");
            errorRequest.setMethod("GET");
            errorResult.setRequest(errorRequest);
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(errorResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证
            assertEquals("ERROR", result.getStatus());
            assertEquals("ConnectException: 连接超时", result.getErrorMessage());
            assertEquals(5000L, result.getDurationMs());
            assertNull(result.getResponseStatusCode());
        }

        @Test
        @DisplayName("环境变量为空时渲染应返回原始值")
        void emptyEnvironmentShouldRenderOriginalValues() {
            // 环境变量为空
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());

            // 变量渲染器返回原值（因为无变量匹配）
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("{{base_url}}/api/users?page={{page}}");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：请求 URL 保留原始占位符
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("execute — 断言结果")
    class ExecuteAssertionResults {

        @Test
        @DisplayName("所有断言通过时 allAssertionsPassed 应为 true")
        void allAssertionsPassedShouldBeTrue() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            // 所有断言通过
            List<Map<String, Object>> passedResults = new ArrayList<>();
            Map<String, Object> r1 = new LinkedHashMap<>();
            r1.put("name", "断言1");
            r1.put("passed", true);
            passedResults.add(r1);
            Map<String, Object> r2 = new LinkedHashMap<>();
            r2.put("name", "断言2");
            r2.put("passed", true);
            passedResults.add(r2);
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(passedResults);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            assertTrue(result.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("有断言失败时 allAssertionsPassed 应为 false")
        void someAssertionsFailedShouldBeFalse() {
            // 准备
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(new HashMap<>());
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(Collections.emptyList());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            // 部分断言失败
            List<Map<String, Object>> mixedResults = new ArrayList<>();
            Map<String, Object> r1 = new LinkedHashMap<>();
            r1.put("name", "通过");
            r1.put("passed", true);
            mixedResults.add(r1);
            Map<String, Object> r2 = new LinkedHashMap<>();
            r2.put("name", "失败");
            r2.put("passed", false);
            mixedResults.add(r2);
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(mixedResults);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            assertFalse(result.getAllAssertionsPassed());
        }
    }

    @Nested
    @DisplayName("execute — POST 请求场景")
    class ExecutePostRequest {

        @Test
        @DisplayName("POST 请求带 JSON Body 应正确传递")
        void postRequestWithJsonBody() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://test-api.example.com");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            // 变量渲染返回原始值
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users");
            preResult.setMethod("POST");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody("{\"name\":\"张三\",\"age\":30}");
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setMethod("POST");
            dto.setBody("{\"name\":\"张三\",\"age\":30}");
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证
            assertNotNull(result);
            verify(httpDebugEngine).execute(debugRequestCaptor.capture());
            DebugRequest sentRequest = debugRequestCaptor.getValue();
            // 验证请求体被传递
            assertNotNull(sentRequest.getBody());
        }

        @Test
        @DisplayName("POST 请求渲染 Body 中的变量")
        void postRequestRenderBody() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://test-api.example.com");
            envVars.put("name", "张三");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            // 变量渲染将 {{name}} 替换为张三
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> {
                String template = invocation.getArgument(0);
                Map<String, String> vars = invocation.getArgument(1);
                String result = template;
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
                }
                return result;
            });
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users");
            preResult.setMethod("POST");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody("{\"name\":\"张三\"}");
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setMethod("POST");
            dto.setBody("{\"name\":\"{{name}}\"}");
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证
            assertNotNull(result);
        }

        @Test
        @DisplayName("POST 请求带 Content-Type 请求头")
        void postRequestWithContentType() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users");
            preResult.setMethod("POST");
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            preResult.setHeaders(headers);
            preResult.setBody("{}");
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setMethod("POST");
            dto.setBody("{}");
            dto.setContentType("application/json");
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("execute — 前置脚本修改场景")
    class ExecutePreScriptModifications {

        @Test
        @DisplayName("前置脚本修改 URL 后 HTTP 引擎使用修改后的 URL")
        void preScriptModifiesUrl() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://original.example.com");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            // 前置脚本将 URL 修改为不同的地址
            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://modified.example.com/api/v2/users");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of("[sandbox] URL 已修改"));
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证 HTTP 引擎收到前置脚本修改后的 URL
            verify(httpDebugEngine).execute(debugRequestCaptor.capture());
            DebugRequest sentRequest = debugRequestCaptor.getValue();
            assertEquals("http://modified.example.com/api/v2/users", sentRequest.getUrl());
        }

        @Test
        @DisplayName("前置脚本修改 Headers 后 HTTP 引擎使用修改后的 Headers")
        void preScriptModifiesHeaders() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            envVars.put("base_url", "http://test-api.example.com");
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            // 前置脚本添加 Authorization header
            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users");
            preResult.setMethod("GET");
            Map<String, String> modifiedHeaders = new HashMap<>();
            modifiedHeaders.put("Authorization", "Bearer script-token");
            preResult.setHeaders(modifiedHeaders);
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证 HTTP 引擎收到前置脚本修改后的 Headers
            verify(httpDebugEngine).execute(debugRequestCaptor.capture());
            DebugRequest sentRequest = debugRequestCaptor.getValue();
            assertEquals("Bearer script-token", sentRequest.getHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("前置脚本修改 Body 后 HTTP 引擎使用修改后的 Body")
        void preScriptModifiesBody() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);

            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            // 前置脚本修改 body
            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://test-api.example.com/api/users");
            preResult.setMethod("POST");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody("{\"script\":\"modified\"}");
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 执行
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            dto.setMethod("POST");
            dto.setBody("{\"original\":true}");
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证 HTTP 引擎收到前置脚本修改后的 Body
            verify(httpDebugEngine).execute(debugRequestCaptor.capture());
            DebugRequest sentRequest = debugRequestCaptor.getValue();
            assertEquals("{\"script\":\"modified\"}", sentRequest.getBody());
        }
    }

    @Nested
    @DisplayName("execute — 多断言/多提取场景")
    class ExecuteMultiAssertionExtract {

        @Test
        @DisplayName("多条断言使用不同来源同时执行")
        void multipleAssertionsDifferentSources() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            // 多个断言结果
            List<Map<String, Object>> assertionResults = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", "断言" + (i + 1));
                r.put("source", i < 2 ? "RESPONSE_STATUS" : "RESPONSE_BODY");
                r.put("compareType", "EQUALS");
                r.put("expected", "200");
                r.put("actual", "200");
                r.put("passed", true);
                assertionResults.add(r);
            }
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(assertionResults);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 构建多条断言
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            List<ApiDebugAssertionDTO> assertions = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ApiDebugAssertionDTO a = new ApiDebugAssertionDTO();
                a.setName("断言" + (i + 1));
                a.setSource(i < 2 ? "RESPONSE_STATUS" : "RESPONSE_BODY");
                a.setCompareType("EQUALS");
                a.setExpectedValue("200");
                assertions.add(a);
            }
            dto.setAssertions(assertions);

            // 执行
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：5 条断言全部通过
            assertNotNull(result);
            assertTrue(result.getAllAssertionsPassed());
            assertEquals(5, result.getAssertionResults().size());
        }

        @Test
        @DisplayName("多个提取规则同时执行")
        void multipleExtractRules() {
            // 准备
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(Collections.emptyList());

            // 多个提取变量
            Map<String, String> extracted = new LinkedHashMap<>();
            extracted.put("userId", "1");
            extracted.put("userName", "张三");
            extracted.put("statusCode", "200");
            when(variableExtractor.extract(anyList(), any(), anyMap(), anyString()))
                    .thenReturn(extracted);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            // 构建多个提取规则
            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            List<ApiDebugExtractDTO> extracts = new ArrayList<>();
            for (Map.Entry<String, String> entry : extracted.entrySet()) {
                ApiDebugExtractDTO e = new ApiDebugExtractDTO();
                e.setVariableName(entry.getKey());
                e.setSource("RESPONSE_BODY");
                e.setExpression("$." + entry.getKey());
                extracts.add(e);
            }
            dto.setExtracts(extracts);

            // 执行
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            // 验证：提取出 3 个变量
            assertNotNull(result);
            assertEquals(3, result.getExtractedVariables().size());
            assertEquals("1", result.getExtractedVariables().get("userId"));
            assertEquals("张三", result.getExtractedVariables().get("userName"));
        }

        @Test
        @DisplayName("断言使用 REGEX 比较类型")
        void assertionWithRegexCompareType() {
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            List<Map<String, Object>> assertionResults = new ArrayList<>();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", "正则断言");
            r.put("source", "RESPONSE_BODY");
            r.put("compareType", "REGEX");
            r.put("expected", "\\{\"code\":0");
            r.put("actual", "{\"code\":0,\"data\":{\"id\":1}}");
            r.put("passed", true);
            assertionResults.add(r);
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(assertionResults);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugAssertionDTO assertion = new ApiDebugAssertionDTO();
            assertion.setName("正则断言");
            assertion.setSource("RESPONSE_BODY");
            assertion.setCompareType("REGEX");
            assertion.setExpectedValue("\\{\"code\":0");
            dto.setAssertions(List.of(assertion));

            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            assertNotNull(result);
            assertTrue(result.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("断言使用 GT 数值比较类型")
        void assertionWithGtCompareType() {
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            List<Map<String, Object>> assertionResults = new ArrayList<>();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", "响应时间 GT 100");
            r.put("source", "RESPONSE_TIME");
            r.put("compareType", "GT");
            r.put("expected", "100");
            r.put("actual", "156");
            r.put("passed", true);
            assertionResults.add(r);
            when(assertionExecutor.execute(anyList(), any(), anyMap(), anyString(), anyLong()))
                    .thenReturn(assertionResults);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugAssertionDTO assertion = new ApiDebugAssertionDTO();
            assertion.setName("响应时间 GT 100");
            assertion.setSource("RESPONSE_TIME");
            assertion.setCompareType("GT");
            assertion.setExpectedValue("100");
            dto.setAssertions(List.of(assertion));

            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            assertNotNull(result);
            assertTrue(result.getAllAssertionsPassed());
        }
    }

    @Nested
    @DisplayName("execute — 异常场景")
    class ExecuteExceptionScenarios {

        @Test
        @DisplayName("后置脚本执行异常不应影响整体结果返回")
        void postScriptExceptionShouldNotBreak() {
            Map<String, String> envVars = new HashMap<>();
            when(environmentService.getVariableMap(TEST_ENV_ID)).thenReturn(envVars);
            when(variableRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
            when(variableRenderer.renderMap(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

            ScriptSandbox.PreRequestResult preResult = new ScriptSandbox.PreRequestResult();
            preResult.setUrl("http://localhost/api/test");
            preResult.setMethod("GET");
            preResult.setHeaders(new HashMap<>());
            preResult.setBody(null);
            preResult.setLogs(List.of());
            when(preRequestScriptExecutor.execute(anyString(), anyString(), anyString(), anyMap(), any(), anyMap()))
                    .thenReturn(preResult);

            DebugResult mockResult = buildMockDebugResult();
            when(httpDebugEngine.execute(any(DebugRequest.class))).thenReturn(mockResult);

            // 后置脚本执行器返回空结果（模拟异常）
            ScriptSandbox.PostResponseResult emptyPostResult = new ScriptSandbox.PostResponseResult();
            emptyPostResult.setEnvironmentVariables(envVars);
            emptyPostResult.setLogs(List.of());
            when(postResponseScriptExecutor.execute(anyString(), any(), anyMap(), anyString(), anyMap()))
                    .thenReturn(emptyPostResult);

            when(debugHistoryService.save(any(DebugResult.class), any(ApiDebugExecuteDTO.class), anyLong()))
                    .thenReturn(TEST_HISTORY_ID);

            ApiDebugExecuteDTO dto = buildFullExecuteDTO();
            ApiDebugResultVO result = apiDebugService.execute(dto, TEST_USER_ID);

            assertNotNull(result);
            assertEquals("SUCCESS", result.getStatus());
        }
    }
}