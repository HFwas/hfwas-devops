package com.hfwas.devops.apitest.history;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.dto.ApiDebugExecuteDTO;
import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResponse;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import com.hfwas.devops.apitest.history.mapper.DebugHistoryMapper;
import com.hfwas.devops.apitest.history.service.DebugHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DebugHistoryService — 调试历史业务层单元测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 重点测试新增的 save() 方法：
 * - 完整字段映射
 * - 边界条件（无响应、无断言、无提取变量）
 * - 自动生成名称
 * - 审计字段自动填充
 *
 * @author hfwas
 */
@DisplayName("DebugHistoryService — 调试历史业务测试")
class DebugHistoryServiceSaveTest extends BaseApiTest {

    @Autowired
    private DebugHistoryService debugHistoryService;

    @Autowired
    private DebugHistoryMapper debugHistoryMapper;

    private ApiDebugExecuteDTO baseDto;

    @BeforeEach
    void setUp() {
        baseDto = new ApiDebugExecuteDTO();
        baseDto.setProjectId(1001L);
        baseDto.setDefinitionId(2001L);
        baseDto.setEnvironmentId(100L);
        baseDto.setUrl("http://test-api.example.com/api/users?page=1");
        baseDto.setMethod("GET");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token-123");
        headers.put("Content-Type", "application/json");
        baseDto.setHeaders(headers);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("page", "1");
        queryParams.put("size", "20");
        baseDto.setQueryParams(queryParams);

        baseDto.setBody("{\"filter\":\"active\"}");
        baseDto.setContentType("application/json");
    }

    /**
     * 构建完整的 DebugResult
     */
    private DebugResult buildFullDebugResult() {
        DebugResult result = new DebugResult();

        DebugRequest request = new DebugRequest();
        request.setUrl("http://test-api.example.com/api/users?page=1");
        request.setMethod("GET");
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("Authorization", "Bearer token-123");
        reqHeaders.put("Content-Type", "application/json");
        request.setHeaders(reqHeaders);
        Map<String, String> reqQueryParams = new HashMap<>();
        reqQueryParams.put("page", "1");
        reqQueryParams.put("size", "20");
        request.setQueryParams(reqQueryParams);
        request.setBody("{\"filter\":\"active\"}");
        request.setContentType("application/json");
        result.setRequest(request);

        DebugResponse response = new DebugResponse();
        response.setStatusCode(200);
        Map<String, String> respHeaders = new HashMap<>();
        respHeaders.put("Content-Type", "application/json;charset=UTF-8");
        respHeaders.put("X-Request-Id", "req-abc-123");
        response.setHeaders(respHeaders);
        response.setBody("{\"code\":0,\"data\":[{\"id\":1,\"name\":\"张三\"}]}");
        response.setContentType("application/json;charset=UTF-8");
        response.setResponseSize(64L);
        result.setResponse(response);

        result.setDurationMs(156L);
        result.setStatus("SUCCESS");

        // 断言结果
        List<Map<String, Object>> assertionResults = new ArrayList<>();
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("name", "状态码为200");
        assertion.put("source", "RESPONSE_STATUS");
        assertion.put("compareType", "EQUALS");
        assertion.put("expected", "200");
        assertion.put("actual", "200");
        assertion.put("passed", true);
        assertionResults.add(assertion);
        result.setAssertionResults(assertionResults);
        result.setAllAssertionsPassed(true);

        // 提取变量
        Map<String, String> extracted = new HashMap<>();
        extracted.put("userId", "1");
        result.setExtractedVariables(extracted);

        // 脚本日志
        result.setPreRequestLogs(List.of("[sandbox] 前置脚本执行完成"));
        result.setPostResponseLogs(List.of("[sandbox] 后置脚本执行完成"));

        return result;
    }

    @Nested
    @DisplayName("save — 保存调试历史")
    class Save {

        @Test
        @DisplayName("保存完整调试结果应返回历史 ID")
        void saveFullResultShouldReturnHistoryId() {
            DebugResult debugResult = buildFullDebugResult();
            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            assertNotNull(historyId);
            assertTrue(historyId > 0);
        }

        @Test
        @DisplayName("保存后数据库应包含完整记录")
        void savedRecordShouldContainAllFields() {
            DebugResult debugResult = buildFullDebugResult();
            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertNotNull(saved);

            // 基本信息
            assertEquals(1001L, saved.getProjectId());
            assertEquals(2001L, saved.getDefinitionId());
            assertEquals(100L, saved.getEnvironmentId());

            // 自动生成的名称
            assertTrue(saved.getName().contains("GET"));
            assertTrue(saved.getName().contains("/api/users"));

            // 请求报文
            assertEquals("http://test-api.example.com/api/users?page=1", saved.getRequestUrl());
            assertEquals("GET", saved.getRequestMethod());
            assertEquals("Bearer token-123", saved.getRequestHeaders().get("Authorization"));
            assertEquals("1", saved.getRequestQuery().get("page"));
            assertEquals("{\"filter\":\"active\"}", saved.getRequestBody());
            assertEquals("application/json", saved.getRequestContentType());

            // 响应报文
            assertEquals(200, saved.getResponseStatusCode());
            assertEquals("application/json;charset=UTF-8", saved.getResponseContentType());
            assertEquals(64L, saved.getResponseSize());
            assertTrue(saved.getResponseBody().contains("张三"));

            // 调试信息
            assertEquals(156L, saved.getDurationMs());
            assertEquals("SUCCESS", saved.getStatus());
            assertNull(saved.getErrorMessage());

            // 断言结果
            assertNotNull(saved.getAssertionResults());
            assertEquals(1, saved.getAssertionResults().size());
            assertEquals(1, saved.getAllAssertionsPassed());

            // 提取变量
            assertNotNull(saved.getExtractedVariables());
            assertEquals("1", saved.getExtractedVariables().get("userId"));

            // 审计字段
            assertEquals(1L, saved.getCreateBy().longValue());
            assertNotNull(saved.getCreateTime());
        }

        @Test
        @DisplayName("保存 ERROR 状态（无响应）应正确映射")
        void saveErrorStatusShouldMapCorrectly() {
            DebugResult debugResult = new DebugResult();

            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:1/api/test");
            request.setMethod("GET");
            debugResult.setRequest(request);

            // 无响应
            debugResult.setResponse(null);
            debugResult.setDurationMs(5000L);
            debugResult.setStatus("ERROR");
            debugResult.setErrorMessage("ConnectException: 连接拒绝");

            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertNotNull(saved);
            assertEquals("ERROR", saved.getStatus());
            assertEquals("ConnectException: 连接拒绝", saved.getErrorMessage());
            assertNull(saved.getResponseStatusCode());
            assertNull(saved.getResponseBody());
            assertEquals(5000L, saved.getDurationMs());
        }

        @Test
        @DisplayName("无断言时应保存为 null")
        void saveNoAssertionsShouldStoreNull() {
            DebugResult debugResult = buildFullDebugResult();
            debugResult.setAssertionResults(null);
            debugResult.setAllAssertionsPassed(null);

            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertNull(saved.getAssertionResults());
            assertNull(saved.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("无提取变量时应保存为 null")
        void saveNoExtractedVariablesShouldStoreNull() {
            DebugResult debugResult = buildFullDebugResult();
            debugResult.setExtractedVariables(null);

            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertNull(saved.getExtractedVariables());
        }

        @Test
        @DisplayName("名称超过 60 字符应被截断")
        void longUrlShouldBeTruncated() {
            DebugResult debugResult = buildFullDebugResult();
            // 构造超长 URL
            String longUrl = "http://test-api.example.com/" + "a".repeat(100) + "?param=" + "b".repeat(50);
            debugResult.getRequest().setUrl(longUrl);

            baseDto.setUrl(longUrl);
            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertNotNull(saved.getName());
            // "GET " (4) + truncated URL (60) = 64
            assertTrue(saved.getName().length() <= 64);
        }

        @Test
        @DisplayName("多次保存应生成不同的历史记录")
        void multipleSavesShouldGenerateDifferentRecords() {
            DebugResult debugResult = buildFullDebugResult();

            Long id1 = debugHistoryService.save(debugResult, baseDto, 1L);
            Long id2 = debugHistoryService.save(debugResult, baseDto, 1L);

            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2);

            DebugHistoryEntity saved1 = debugHistoryMapper.selectById(id1);
            DebugHistoryEntity saved2 = debugHistoryMapper.selectById(id2);
            assertNotNull(saved1);
            assertNotNull(saved2);
        }

        @Test
        @DisplayName("FAILURE 状态（4xx 响应）应正确保存")
        void saveFailureStatusShouldMapCorrectly() {
            DebugResult debugResult = buildFullDebugResult();
            debugResult.getResponse().setStatusCode(400);
            debugResult.setStatus("FAILURE");
            debugResult.getResponse().setBody("{\"code\":400,\"message\":\"参数错误\"}");
            debugResult.getResponse().setResponseSize(32L);

            // 断言失败
            List<Map<String, Object>> assertionResults = new ArrayList<>();
            Map<String, Object> assertion = new LinkedHashMap<>();
            assertion.put("name", "状态码为200");
            assertion.put("passed", false);
            assertion.put("actual", "400");
            assertion.put("expected", "200");
            assertionResults.add(assertion);
            debugResult.setAssertionResults(assertionResults);
            debugResult.setAllAssertionsPassed(false);

            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertEquals("FAILURE", saved.getStatus());
            assertEquals(400, saved.getResponseStatusCode());
            assertEquals(0, saved.getAllAssertionsPassed());
        }
    }

    @Nested
    @DisplayName("save — 名称生成")
    class NameGeneration {

        @Test
        @DisplayName("名称应为 \"METHOD URL\" 格式")
        void nameShouldBeMethodUrlFormat() {
            DebugResult debugResult = buildFullDebugResult();
            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertEquals("GET http://test-api.example.com/api/users?page=1", saved.getName());
        }

        @Test
        @DisplayName("无请求对象时应使用 DTO 中的值")
        void noRequestShouldUseDtoValues() {
            DebugResult debugResult = new DebugResult();
            // 不设置 request
            debugResult.setResponse(null);
            debugResult.setDurationMs(0L);
            debugResult.setStatus("ERROR");

            // 使用 DTO 中的 method 和 url
            Long historyId = debugHistoryService.save(debugResult, baseDto, 1L);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(historyId);
            assertTrue(saved.getName().contains("GET"));
            assertTrue(saved.getName().contains("/api/users"));
            assertEquals("GET", saved.getRequestMethod());
            assertEquals("http://test-api.example.com/api/users?page=1", saved.getRequestUrl());
        }
    }
}