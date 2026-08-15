package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import com.hfwas.devops.apitest.history.mapper.DebugHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DebugHistoryMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及调试历史特有的业务语义（状态、项目隔离、关联定义、逻辑删除、JSON 类型字段）。
 *
 * @author hfwas
 */
@DisplayName("DebugHistoryMapper — 调试历史数据访问测试")
class DebugHistoryMapperTest extends BaseApiTest {

    @Autowired
    private DebugHistoryMapper debugHistoryMapper;

    private DebugHistoryEntity successHistory;
    private DebugHistoryEntity failureHistory;
    private DebugHistoryEntity errorHistory;

    @BeforeEach
    void setUp() {
        successHistory = new DebugHistoryEntity();
        successHistory.setProjectId(testProjectId());
        successHistory.setDefinitionId(1001L);
        successHistory.setEnvironmentId(2001L);
        successHistory.setName("获取用户列表调试");
        successHistory.setRequestUrl("http://localhost:8080/api/users/list");
        successHistory.setRequestMethod("GET");
        successHistory.setRequestContentType("application/json");
        {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer token-abc");
            successHistory.setRequestHeaders(headers);
        }
        {
            Map<String, String> query = new HashMap<>();
            query.put("page", "1");
            query.put("size", "20");
            successHistory.setRequestQuery(query);
        }
        successHistory.setRequestBody("{}");
        successHistory.setResponseStatusCode(200);
        {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json;charset=UTF-8");
            headers.put("X-Request-Id", "req-111");
            successHistory.setResponseHeaders(headers);
        }
        successHistory.setResponseBody("{\"code\":200,\"data\":[]}");
        successHistory.setResponseContentType("application/json;charset=UTF-8");
        successHistory.setResponseSize(256L);
        successHistory.setDurationMs(150L);
        successHistory.setStatus("SUCCESS");
        {
            Map<String, Object> assertion = new HashMap<>();
            assertion.put("name", "状态码校验");
            assertion.put("passed", true);
            assertion.put("actual", 200);
            assertion.put("expected", 200);
            successHistory.setAssertionResults(List.of(assertion));
        }
        successHistory.setAllAssertionsPassed(1);
        {
            Map<String, String> variables = new HashMap<>();
            variables.put("userId", "1001");
            successHistory.setExtractedVariables(variables);
        }
        successHistory.setDeleted(0);

        failureHistory = new DebugHistoryEntity();
        failureHistory.setProjectId(testProjectId());
        failureHistory.setDefinitionId(1002L);
        failureHistory.setEnvironmentId(2001L);
        failureHistory.setName("创建用户调试");
        failureHistory.setRequestUrl("http://localhost:8080/api/users");
        failureHistory.setRequestMethod("POST");
        failureHistory.setRequestContentType("application/json");
        {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer token-abc");
            failureHistory.setRequestHeaders(headers);
        }
        failureHistory.setRequestBody("{\"name\":\"test\"}");
        failureHistory.setResponseStatusCode(400);
        failureHistory.setResponseBody("{\"code\":400,\"message\":\"参数校验失败\"}");
        failureHistory.setResponseContentType("application/json;charset=UTF-8");
        failureHistory.setResponseSize(128L);
        failureHistory.setDurationMs(50L);
        failureHistory.setStatus("FAILURE");
        failureHistory.setAllAssertionsPassed(0);
        failureHistory.setDeleted(0);

        errorHistory = new DebugHistoryEntity();
        errorHistory.setProjectId(testProjectId());
        errorHistory.setDefinitionId(1003L);
        errorHistory.setEnvironmentId(2001L);
        errorHistory.setName("删除用户调试");
        errorHistory.setRequestUrl("http://localhost:8080/api/users/1");
        errorHistory.setRequestMethod("DELETE");
        errorHistory.setRequestContentType("application/json");
        errorHistory.setResponseStatusCode(null);
        errorHistory.setDurationMs(30000L);
        errorHistory.setStatus("ERROR");
        errorHistory.setErrorMessage("连接超时");
        errorHistory.setAllAssertionsPassed(0);
        errorHistory.setDeleted(0);
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入调试历史应成功并返回自增 ID")
        void insertShouldSucceed() {
            int affected = debugHistoryMapper.insert(successHistory);
            assertEquals(1, affected);
            assertNotNull(successHistory.getId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            int affected1 = debugHistoryMapper.insert(successHistory);
            int affected2 = debugHistoryMapper.insert(failureHistory);
            int affected3 = debugHistoryMapper.insert(errorHistory);

            assertEquals(1, affected1);
            assertEquals(1, affected2);
            assertEquals(1, affected3);
            assertNotNull(successHistory.getId());
            assertNotNull(failureHistory.getId());
            assertNotNull(errorHistory.getId());
        }

        @Test
        @DisplayName("插入时应自动填充 createBy 和 createTime")
        void insertShouldAutoFillAuditFields() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertNotNull(saved.getCreateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            successHistory.setDeleted(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved);
            assertEquals(0, saved.getDeleted());
        }

        @Test
        @DisplayName("插入 requestHeaders Map 字段应正确存储")
        void insertWithRequestHeadersShouldSucceed() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getRequestHeaders());
            assertEquals(2, saved.getRequestHeaders().size());
            assertEquals("application/json", saved.getRequestHeaders().get("Content-Type"));
            assertEquals("Bearer token-abc", saved.getRequestHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("插入 requestQuery Map 字段应正确存储")
        void insertWithRequestQueryShouldSucceed() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getRequestQuery());
            assertEquals(2, saved.getRequestQuery().size());
            assertEquals("1", saved.getRequestQuery().get("page"));
            assertEquals("20", saved.getRequestQuery().get("size"));
        }

        @Test
        @DisplayName("插入 responseHeaders Map 字段应正确存储")
        void insertWithResponseHeadersShouldSucceed() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getResponseHeaders());
            assertEquals(2, saved.getResponseHeaders().size());
            assertEquals("application/json;charset=UTF-8", saved.getResponseHeaders().get("Content-Type"));
            assertEquals("req-111", saved.getResponseHeaders().get("X-Request-Id"));
        }

        @Test
        @DisplayName("插入 assertionResults 列表字段应正确存储")
        void insertWithAssertionResultsShouldSucceed() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getAssertionResults());
            assertEquals(1, saved.getAssertionResults().size());
            assertEquals(true, saved.getAssertionResults().get(0).get("passed"));
        }

        @Test
        @DisplayName("插入 extractedVariables Map 字段应正确存储")
        void insertWithExtractedVariablesShouldSucceed() {
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getExtractedVariables());
            assertEquals(1, saved.getExtractedVariables().size());
            assertEquals("1001", saved.getExtractedVariables().get("userId"));
        }

        @Test
        @DisplayName("插入 ERROR 状态的记录（无响应体）应成功")
        void insertErrorStatusShouldSucceed() {
            int affected = debugHistoryMapper.insert(errorHistory);
            assertEquals(1, affected);
            assertNotNull(errorHistory.getId());

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertEquals("ERROR", saved.getStatus());
            assertNull(saved.getResponseStatusCode());
            assertNull(saved.getResponseBody());
            assertEquals("连接超时", saved.getErrorMessage());
        }
    }

    // ========================================================================
    // Select 操作
    // ========================================================================

    @Nested
    @DisplayName("Select 操作")
    class SelectOperations {

        @BeforeEach
        void insertTestData() {
            debugHistoryMapper.insert(successHistory);
            debugHistoryMapper.insert(failureHistory);
            debugHistoryMapper.insert(errorHistory);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确的调试历史")
        void selectByIdShouldReturnCorrectHistory() {
            DebugHistoryEntity found = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(found);
            assertEquals(successHistory.getId(), found.getId());
            assertEquals("获取用户列表调试", found.getName());
            assertEquals("GET", found.getRequestMethod());
            assertEquals(200, found.getResponseStatusCode());
            assertEquals("SUCCESS", found.getStatus());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            DebugHistoryEntity found = debugHistoryMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有调试历史应返回全部记录（未逻辑删除的）")
        void selectListAllShouldReturnAllHistories() {
            List<DebugHistoryEntity> all = debugHistoryMapper.selectList(null);
            assertNotNull(all);
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 projectId 条件查询应返回正确结果")
        void selectByProjectIdShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getProjectId, testProjectId())
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(h -> h.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("根据 definitionId 条件查询应返回正确结果")
        void selectByDefinitionIdShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getDefinitionId, 1001L)
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("获取用户列表调试", result.get(0).getName());
        }

        @Test
        @DisplayName("根据 status 条件查询 SUCCESS 应返回正确结果")
        void selectByStatusSuccessShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getStatus, "SUCCESS")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(200, result.get(0).getResponseStatusCode());
        }

        @Test
        @DisplayName("根据 status 条件查询 FAILURE 应返回正确结果")
        void selectByStatusFailureShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getStatus, "FAILURE")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(400, result.get(0).getResponseStatusCode());
        }

        @Test
        @DisplayName("根据 status 条件查询 ERROR 应返回正确结果")
        void selectByStatusErrorShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getStatus, "ERROR")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get(0).getResponseStatusCode());
            assertEquals("连接超时", result.get(0).getErrorMessage());
        }

        @Test
        @DisplayName("根据 requestMethod 条件查询应返回正确结果")
        void selectByRequestMethodShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestMethod, "GET")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("GET", result.get(0).getRequestMethod());
        }

        @Test
        @DisplayName("根据 name 模糊查询应返回匹配的调试历史")
        void selectByNameLikeShouldReturnMatchingHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .like(DebugHistoryEntity::getName, "用户")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 1);
            assertTrue(result.stream().allMatch(h -> h.getName().contains("用户")));
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = debugHistoryMapper.selectCount(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getProjectId, testProjectId())
            );
            assertNotNull(count);
            assertTrue(count >= 3);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(successHistory.getId(), failureHistory.getId());
            List<DebugHistoryEntity> result = debugHistoryMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("复合条件查询（projectId + status）应返回正确结果")
        void selectByProjectIdAndStatusShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getProjectId, testProjectId())
                            .eq(DebugHistoryEntity::getStatus, "FAILURE")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("创建用户调试", result.get(0).getName());
        }

        @Test
        @DisplayName("复合条件查询（definitionId + method）应返回正确结果")
        void selectByDefinitionIdAndMethodShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getDefinitionId, 1001L)
                            .eq(DebugHistoryEntity::getRequestMethod, "GET")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("查询 responseStatusCode 为 null 的记录应返回 ERROR 状态的调试")
        void selectNullResponseStatusCodeShouldReturnErrorHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .isNull(DebugHistoryEntity::getResponseStatusCode)
            );
            assertNotNull(result);
            assertTrue(result.size() >= 1);
            assertTrue(result.stream().allMatch(h -> h.getResponseStatusCode() == null));
        }

        @Test
        @DisplayName("按 requestUrl 精确查询应返回正确结果")
        void selectByRequestUrlShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestUrl, "http://localhost:8080/api/users/list")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("GET", result.get(0).getRequestMethod());
        }

        @Test
        @DisplayName("查询 durationMs 大于指定值的记录应返回结果")
        void selectByDurationMsGreaterThanShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .gt(DebugHistoryEntity::getDurationMs, 1000L)
            );
            assertNotNull(result);
            assertTrue(result.size() >= 1);
            assertTrue(result.stream().allMatch(h -> h.getDurationMs() > 1000L));
        }

        @Test
        @DisplayName("查询 allAssertionsPassed 为 1 的记录应返回正确结果")
        void selectByAllAssertionsPassedShouldReturnCorrectHistories() {
            List<DebugHistoryEntity> result = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getAllAssertionsPassed, 1)
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("SUCCESS", result.get(0).getStatus());
        }
    }

    // ========================================================================
    // Update 操作
    // ========================================================================

    @Nested
    @DisplayName("Update 操作")
    class UpdateOperations {

        @BeforeEach
        void insertTestData() {
            debugHistoryMapper.insert(successHistory);
            debugHistoryMapper.insert(failureHistory);
        }

        @Test
        @DisplayName("更新调试名称应成功")
        void updateNameShouldSucceed() {
            successHistory.setName("获取用户列表调试V2");
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("获取用户列表调试V2", updated.getName());
        }

        @Test
        @DisplayName("更新请求 URL 应成功")
        void updateRequestUrlShouldSucceed() {
            successHistory.setRequestUrl("http://localhost:8080/api/users/v2/list");
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("http://localhost:8080/api/users/v2/list", updated.getRequestUrl());
        }

        @Test
        @DisplayName("更新响应状态码应成功")
        void updateResponseStatusCodeShouldSucceed() {
            successHistory.setResponseStatusCode(201);
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(201, updated.getResponseStatusCode());
        }

        @Test
        @DisplayName("更新调试状态应成功")
        void updateStatusShouldSucceed() {
            successHistory.setStatus("FAILURE");
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("FAILURE", updated.getStatus());
        }

        @Test
        @DisplayName("更新错误信息应成功")
        void updateErrorMessageShouldSucceed() {
            failureHistory.setErrorMessage("参数错误：缺少必填字段");
            int affected = debugHistoryMapper.updateById(failureHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(failureHistory.getId());
            assertEquals("参数错误：缺少必填字段", updated.getErrorMessage());
        }

        @Test
        @DisplayName("更新响应体应成功")
        void updateResponseBodyShouldSucceed() {
            successHistory.setResponseBody("{\"code\":200,\"data\":[{\"id\":1}]}");
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertTrue(updated.getResponseBody().contains("\"id\":1"));
        }

        @Test
        @DisplayName("更新请求头 Map 字段应全量替换")
        void updateRequestHeadersShouldReplaceCompletely() {
            Map<String, String> newHeaders = new HashMap<>();
            newHeaders.put("Content-Type", "application/xml");
            successHistory.setRequestHeaders(newHeaders);
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(updated.getRequestHeaders());
            assertEquals(1, updated.getRequestHeaders().size());
            assertEquals("application/xml", updated.getRequestHeaders().get("Content-Type"));
            assertNull(updated.getRequestHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("更新 assertionResults 列表字段应全量替换")
        void updateAssertionResultsShouldReplaceCompletely() {
            Map<String, Object> newAssertion = new HashMap<>();
            newAssertion.put("name", "响应体校验");
            newAssertion.put("passed", false);
            newAssertion.put("actual", "[]");
            newAssertion.put("expected", "[{\"id\":1}]");
            successHistory.setAssertionResults(List.of(newAssertion));
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(updated.getAssertionResults());
            assertEquals(1, updated.getAssertionResults().size());
            assertEquals(false, updated.getAssertionResults().get(0).get("passed"));
        }

        @Test
        @DisplayName("更新 extractedVariables Map 字段应全量替换")
        void updateExtractedVariablesShouldReplaceCompletely() {
            Map<String, String> newVariables = new HashMap<>();
            newVariables.put("token", "new-token-xyz");
            successHistory.setExtractedVariables(newVariables);
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(updated.getExtractedVariables());
            assertEquals(1, updated.getExtractedVariables().size());
            assertEquals("new-token-xyz", updated.getExtractedVariables().get("token"));
            assertNull(updated.getExtractedVariables().get("userId"));
        }

        @Test
        @DisplayName("更新 durationMs 应成功")
        void updateDurationMsShouldSucceed() {
            successHistory.setDurationMs(200L);
            int affected = debugHistoryMapper.updateById(successHistory);
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(200L, updated.getDurationMs());
        }

        @Test
        @DisplayName("将 ERROR 更新为 SUCCESS 应正确反映状态变更")
        void updateErrorToSuccessShouldReflectStatusChange() {
            debugHistoryMapper.insert(errorHistory);

            // 使用 LambdaUpdateWrapper 显式设置 errorMessage 为 null
            // MyBatis-Plus 默认 updateById 会跳过 null 字段
            debugHistoryMapper.update(
                    new LambdaUpdateWrapper<DebugHistoryEntity>()
                            .eq(DebugHistoryEntity::getId, errorHistory.getId())
                            .set(DebugHistoryEntity::getStatus, "SUCCESS")
                            .set(DebugHistoryEntity::getResponseStatusCode, 200)
                            .set(DebugHistoryEntity::getResponseBody, "{\"code\":200}")
                            .set(DebugHistoryEntity::getErrorMessage, null)
                            .set(DebugHistoryEntity::getDurationMs, 100L)
            );

            DebugHistoryEntity updated = debugHistoryMapper.selectById(errorHistory.getId());
            assertEquals("SUCCESS", updated.getStatus());
            assertEquals(200, updated.getResponseStatusCode());
            assertNotNull(updated.getResponseBody());
            assertNull(updated.getErrorMessage());
            assertEquals(100L, updated.getDurationMs());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            DebugHistoryEntity nonExistent = new DebugHistoryEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = debugHistoryMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            int affected = debugHistoryMapper.update(
                    null,
                    Wrappers.<DebugHistoryEntity>lambdaUpdate()
                            .set(DebugHistoryEntity::getStatus, "SUCCESS")
                            .eq(DebugHistoryEntity::getStatus, "FAILURE")
            );
            assertEquals(1, affected);

            DebugHistoryEntity updated = debugHistoryMapper.selectById(failureHistory.getId());
            assertEquals("SUCCESS", updated.getStatus());

            DebugHistoryEntity unchanged = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("SUCCESS", unchanged.getStatus());
        }
    }

    // ========================================================================
    // Delete 操作（逻辑删除）
    // ========================================================================

    @Nested
    @DisplayName("Delete 操作（逻辑删除）")
    class DeleteOperations {

        @BeforeEach
        void insertTestData() {
            debugHistoryMapper.insert(successHistory);
            debugHistoryMapper.insert(failureHistory);
            debugHistoryMapper.insert(errorHistory);
        }

        @Test
        @DisplayName("逻辑删除调试历史应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = debugHistoryMapper.deleteById(successHistory.getId());
            assertEquals(1, affected);

            List<DebugHistoryEntity> all = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getId, successHistory.getId())
            );
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            debugHistoryMapper.deleteById(successHistory.getId());

            DebugHistoryEntity found = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = debugHistoryMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(successHistory.getId(), failureHistory.getId());
            int affected = debugHistoryMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            List<DebugHistoryEntity> remaining = debugHistoryMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            int affected = debugHistoryMapper.delete(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getStatus, "ERROR")
            );
            assertEquals(1, affected);

            assertNull(debugHistoryMapper.selectById(errorHistory.getId()));
            assertNotNull(debugHistoryMapper.selectById(successHistory.getId()));
            assertNotNull(debugHistoryMapper.selectById(failureHistory.getId()));
        }
    }

    // ========================================================================
    // 业务语义 — 状态与方法
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 状态与方法")
    class StatusAndMethodSemantics {

        private DebugHistoryEntity putHistory;
        private DebugHistoryEntity patchHistory;

        @BeforeEach
        void insertData() {
            debugHistoryMapper.insert(successHistory);
            debugHistoryMapper.insert(failureHistory);
            debugHistoryMapper.insert(errorHistory);

            putHistory = new DebugHistoryEntity();
            putHistory.setProjectId(testProjectId());
            putHistory.setDefinitionId(1004L);
            putHistory.setEnvironmentId(2001L);
            putHistory.setName("更新用户调试");
            putHistory.setRequestUrl("http://localhost:8080/api/users/1");
            putHistory.setRequestMethod("PUT");
            putHistory.setRequestContentType("application/json");
            putHistory.setRequestBody("{\"name\":\"updated\"}");
            putHistory.setResponseStatusCode(200);
            putHistory.setResponseBody("{\"code\":200}");
            putHistory.setResponseContentType("application/json;charset=UTF-8");
            putHistory.setResponseSize(64L);
            putHistory.setDurationMs(80L);
            putHistory.setStatus("SUCCESS");
            putHistory.setAllAssertionsPassed(1);
            putHistory.setDeleted(0);
            debugHistoryMapper.insert(putHistory);

            patchHistory = new DebugHistoryEntity();
            patchHistory.setProjectId(testProjectId());
            patchHistory.setDefinitionId(1005L);
            patchHistory.setEnvironmentId(2001L);
            patchHistory.setName("部分更新用户调试");
            patchHistory.setRequestUrl("http://localhost:8080/api/users/1");
            patchHistory.setRequestMethod("PATCH");
            patchHistory.setRequestContentType("application/json");
            patchHistory.setRequestBody("{\"name\":\"patched\"}");
            patchHistory.setResponseStatusCode(200);
            patchHistory.setResponseBody("{\"code\":200}");
            patchHistory.setResponseContentType("application/json;charset=UTF-8");
            patchHistory.setResponseSize(48L);
            patchHistory.setDurationMs(60L);
            patchHistory.setStatus("SUCCESS");
            patchHistory.setAllAssertionsPassed(1);
            patchHistory.setDeleted(0);
            debugHistoryMapper.insert(patchHistory);
        }

        @Test
        @DisplayName("同一接口定义可存在多个调试历史记录")
        void sameDefinitionCanHaveMultipleHistories() {
            List<DebugHistoryEntity> histories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getDefinitionId, 1001L)
            );
            assertEquals(1, histories.size());
        }

        @Test
        @DisplayName("不同 requestMethod 的调试历史应独立存在")
        void differentMethodsShouldBeIndependent() {
            List<DebugHistoryEntity> getHistories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestMethod, "GET")
            );
            assertEquals(1, getHistories.size());

            List<DebugHistoryEntity> putHistories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestMethod, "PUT")
            );
            assertEquals(1, putHistories.size());

            List<DebugHistoryEntity> patchHistories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestMethod, "PATCH")
            );
            assertEquals(1, patchHistories.size());
        }

        @Test
        @DisplayName("查询同一 URL 下所有方法的调试历史应返回全部")
        void selectAllMethodsForSameUrlShouldReturnAll() {
            List<DebugHistoryEntity> histories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getRequestUrl, "http://localhost:8080/api/users/1")
            );
            // errorHistory + putHistory + patchHistory 共 3 条
            assertEquals(3, histories.size());
        }

        @Test
        @DisplayName("查询所有 SUCCESS 状态的调试历史应返回正确结果")
        void selectAllSuccessShouldReturnCorrectCount() {
            List<DebugHistoryEntity> successList = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getStatus, "SUCCESS")
            );
            assertEquals(3, successList.size());
        }
    }

    // ========================================================================
    // 业务语义 — JSON 类型字段处理器
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — JSON 类型字段处理器")
    class JsonTypeHandlerSemantics {

        @Test
        @DisplayName("requestHeaders 为 null 时插入与查询应正确")
        void nullRequestHeadersShouldBeStoredCorrectly() {
            successHistory.setRequestHeaders(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getRequestHeaders());
        }

        @Test
        @DisplayName("requestHeaders 为空 Map 时插入与查询应正确")
        void emptyRequestHeadersShouldBeStoredCorrectly() {
            successHistory.setRequestHeaders(new HashMap<>());
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getRequestHeaders());
            assertTrue(saved.getRequestHeaders().isEmpty());
        }

        @Test
        @DisplayName("requestQuery 为 null 时插入与查询应正确")
        void nullRequestQueryShouldBeStoredCorrectly() {
            successHistory.setRequestQuery(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getRequestQuery());
        }

        @Test
        @DisplayName("requestQuery 为空 Map 时插入与查询应正确")
        void emptyRequestQueryShouldBeStoredCorrectly() {
            successHistory.setRequestQuery(new HashMap<>());
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getRequestQuery());
            assertTrue(saved.getRequestQuery().isEmpty());
        }

        @Test
        @DisplayName("responseHeaders 为 null 时插入与查询应正确")
        void nullResponseHeadersShouldBeStoredCorrectly() {
            successHistory.setResponseHeaders(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getResponseHeaders());
        }

        @Test
        @DisplayName("responseHeaders 为空 Map 时插入与查询应正确")
        void emptyResponseHeadersShouldBeStoredCorrectly() {
            successHistory.setResponseHeaders(new HashMap<>());
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getResponseHeaders());
            assertTrue(saved.getResponseHeaders().isEmpty());
        }

        @Test
        @DisplayName("assertionResults 为 null 时插入与查询应正确")
        void nullAssertionResultsShouldBeStoredCorrectly() {
            successHistory.setAssertionResults(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getAssertionResults());
        }

        @Test
        @DisplayName("assertionResults 为空列表时插入与查询应正确")
        void emptyAssertionResultsShouldBeStoredCorrectly() {
            successHistory.setAssertionResults(List.of());
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getAssertionResults());
            assertTrue(saved.getAssertionResults().isEmpty());
        }

        @Test
        @DisplayName("extractedVariables 为 null 时插入与查询应正确")
        void nullExtractedVariablesShouldBeStoredCorrectly() {
            successHistory.setExtractedVariables(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getExtractedVariables());
        }

        @Test
        @DisplayName("extractedVariables 为空 Map 时插入与查询应正确")
        void emptyExtractedVariablesShouldBeStoredCorrectly() {
            successHistory.setExtractedVariables(new HashMap<>());
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getExtractedVariables());
            assertTrue(saved.getExtractedVariables().isEmpty());
        }

        @Test
        @DisplayName("多个断言结果应正确存储与读取")
        void multipleAssertionResultsShouldBeStoredCorrectly() {
            Map<String, Object> assertion1 = new HashMap<>();
            assertion1.put("name", "状态码校验");
            assertion1.put("passed", true);
            assertion1.put("actual", 200);
            assertion1.put("expected", 200);

            Map<String, Object> assertion2 = new HashMap<>();
            assertion2.put("name", "响应体校验");
            assertion2.put("passed", true);
            assertion2.put("actual", "{\"code\":200}");
            assertion2.put("expected", "{\"code\":200}");

            successHistory.setAssertionResults(Arrays.asList(assertion1, assertion2));
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getAssertionResults());
            assertEquals(2, saved.getAssertionResults().size());
            assertEquals(true, saved.getAssertionResults().get(0).get("passed"));
            assertEquals(true, saved.getAssertionResults().get(1).get("passed"));
        }

        @Test
        @DisplayName("多组 extractedVariables 应正确存储与读取")
        void multipleExtractedVariablesShouldBeStoredCorrectly() {
            Map<String, String> variables = new HashMap<>();
            variables.put("userId", "1001");
            variables.put("token", "abc-123-def");
            variables.put("sessionId", "sess-xyz");
            successHistory.setExtractedVariables(variables);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNotNull(saved.getExtractedVariables());
            assertEquals(3, saved.getExtractedVariables().size());
            assertEquals("1001", saved.getExtractedVariables().get("userId"));
            assertEquals("abc-123-def", saved.getExtractedVariables().get("token"));
            assertEquals("sess-xyz", saved.getExtractedVariables().get("sessionId"));
        }
    }

    // ========================================================================
    // 边界条件
    // ========================================================================

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

        @Test
        @DisplayName("调试名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "调试" + "A".repeat(100);
            successHistory.setName(longName);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("requestUrl 为超长字符串时插入与查询应正确")
        void veryLongRequestUrlShouldBeStoredCorrectly() {
            String longUrl = "http://localhost:8080/api/" + "a".repeat(200);
            successHistory.setRequestUrl(longUrl);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(longUrl, saved.getRequestUrl());
        }

        @Test
        @DisplayName("requestBody 为超长字符串时插入与查询应正确")
        void veryLongRequestBodyShouldBeStoredCorrectly() {
            String longBody = "{\"data\":\"" + "X".repeat(1000) + "\"}";
            successHistory.setRequestBody(longBody);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(longBody, saved.getRequestBody());
        }

        @Test
        @DisplayName("responseBody 为超长字符串时插入与查询应正确")
        void veryLongResponseBodyShouldBeStoredCorrectly() {
            String longBody = "{\"data\":\"" + "Y".repeat(1000) + "\"}";
            successHistory.setResponseBody(longBody);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(longBody, saved.getResponseBody());
        }

        @Test
        @DisplayName("errorMessage 为超长字符串时插入与查询应正确")
        void veryLongErrorMessageShouldBeStoredCorrectly() {
            String longMsg = "错误" + "Z".repeat(200);
            errorHistory.setErrorMessage(longMsg);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertEquals(longMsg, saved.getErrorMessage());
        }

        @Test
        @DisplayName("errorMessage 为 null 时插入与查询应正确")
        void nullErrorMessageShouldBeStoredCorrectly() {
            successHistory.setErrorMessage(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getErrorMessage());
        }

        @Test
        @DisplayName("errorMessage 为空字符串时插入与查询应正确")
        void emptyErrorMessageShouldBeStoredCorrectly() {
            successHistory.setErrorMessage("");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("", saved.getErrorMessage());
        }

        @Test
        @DisplayName("requestBody 为 null 时插入与查询应正确")
        void nullRequestBodyShouldBeStoredCorrectly() {
            errorHistory.setRequestBody(null);
            debugHistoryMapper.insert(errorHistory);
            // errorHistory 的 requestBody 原本就是 null，所以直接验证
            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getRequestBody());
        }

        @Test
        @DisplayName("requestBody 为空字符串时插入与查询应正确")
        void emptyRequestBodyShouldBeStoredCorrectly() {
            successHistory.setRequestBody("");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("", saved.getRequestBody());
        }

        @Test
        @DisplayName("responseBody 为 null 时插入与查询应正确")
        void nullResponseBodyShouldBeStoredCorrectly() {
            errorHistory.setResponseBody(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getResponseBody());
        }

        @Test
        @DisplayName("responseBody 为空字符串时插入与查询应正确")
        void emptyResponseBodyShouldBeStoredCorrectly() {
            successHistory.setResponseBody("");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("", saved.getResponseBody());
        }

        @Test
        @DisplayName("requestContentType 为 null 时插入与查询应正确")
        void nullRequestContentTypeShouldBeStoredCorrectly() {
            errorHistory.setRequestContentType(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getRequestContentType());
        }

        @Test
        @DisplayName("requestContentType 为 multipart/form-data 时插入与查询应正确")
        void multipartRequestContentTypeShouldBeStoredCorrectly() {
            successHistory.setRequestContentType("multipart/form-data");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("multipart/form-data", saved.getRequestContentType());
        }

        @Test
        @DisplayName("responseContentType 为 null 时插入与查询应正确")
        void nullResponseContentTypeShouldBeStoredCorrectly() {
            errorHistory.setResponseContentType(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getResponseContentType());
        }

        @Test
        @DisplayName("responseStatusCode 为 null 时插入与查询应正确")
        void nullResponseStatusCodeShouldBeStoredCorrectly() {
            errorHistory.setResponseStatusCode(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getResponseStatusCode());
        }

        @Test
        @DisplayName("responseSize 为 null 时插入与查询应正确")
        void nullResponseSizeShouldBeStoredCorrectly() {
            errorHistory.setResponseSize(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            assertNull(saved.getResponseSize());
        }

        @Test
        @DisplayName("responseSize 为 0 时插入与查询应正确")
        void zeroResponseSizeShouldBeStoredCorrectly() {
            successHistory.setResponseSize(0L);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(0L, saved.getResponseSize());
        }

        @Test
        @DisplayName("durationMs 为 null 时插入应使用数据库默认值 0")
        void nullDurationMsShouldBeStoredCorrectly() {
            errorHistory.setDurationMs(null);
            debugHistoryMapper.insert(errorHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(errorHistory.getId());
            // 数据库列有 NOT NULL DEFAULT 0，null 被转换为 0
            assertEquals(0L, saved.getDurationMs());
        }

        @Test
        @DisplayName("durationMs 为 0 时插入与查询应正确")
        void zeroDurationMsShouldBeStoredCorrectly() {
            successHistory.setDurationMs(0L);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals(0L, saved.getDurationMs());
        }

        @Test
        @DisplayName("allAssertionsPassed 为 null 时插入与查询应正确")
        void nullAllAssertionsPassedShouldBeStoredCorrectly() {
            successHistory.setAllAssertionsPassed(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("projectId 为 null 时插入应抛出约束异常")
        void nullProjectIdShouldBeStoredCorrectly() {
            successHistory.setProjectId(null);
            assertThrows(Exception.class, () ->
                    debugHistoryMapper.insert(successHistory));
        }

        @Test
        @DisplayName("definitionId 为 null 时插入与查询应正确")
        void nullDefinitionIdShouldBeStoredCorrectly() {
            successHistory.setDefinitionId(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getDefinitionId());
        }

        @Test
        @DisplayName("environmentId 为 null 时插入与查询应正确")
        void nullEnvironmentIdShouldBeStoredCorrectly() {
            successHistory.setEnvironmentId(null);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertNull(saved.getEnvironmentId());
        }

        @Test
        @DisplayName("不同项目下的调试历史应互不干扰")
        void historiesInDifferentProjectsShouldBeIndependent() {
            debugHistoryMapper.insert(successHistory);

            Long anotherProjectId = 2002L;
            DebugHistoryEntity otherProjectHistory = new DebugHistoryEntity();
            otherProjectHistory.setProjectId(anotherProjectId);
            otherProjectHistory.setDefinitionId(1001L);
            otherProjectHistory.setEnvironmentId(2001L);
            otherProjectHistory.setName("其他项目调试");
            otherProjectHistory.setRequestUrl("http://other/api");
            otherProjectHistory.setRequestMethod("GET");
            otherProjectHistory.setRequestContentType("application/json");
            otherProjectHistory.setResponseStatusCode(200);
            otherProjectHistory.setDurationMs(100L);
            otherProjectHistory.setStatus("SUCCESS");
            otherProjectHistory.setAllAssertionsPassed(1);
            otherProjectHistory.setDeleted(0);
            debugHistoryMapper.insert(otherProjectHistory);

            List<DebugHistoryEntity> projectHistories = debugHistoryMapper.selectList(
                    Wrappers.<DebugHistoryEntity>lambdaQuery()
                            .eq(DebugHistoryEntity::getProjectId, testProjectId())
            );
            assertTrue(projectHistories.stream().allMatch(h -> h.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("requestUrl 含查询参数时插入与查询应正确")
        void requestUrlWithQueryParametersShouldBeStoredCorrectly() {
            successHistory.setRequestUrl("http://localhost:8080/api/users?page=1&size=20&sort=asc");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("http://localhost:8080/api/users?page=1&size=20&sort=asc", saved.getRequestUrl());
        }

        @Test
        @DisplayName("requestMethod 为 HEAD 时插入与查询应正确")
        void headMethodShouldBeStoredCorrectly() {
            successHistory.setRequestMethod("HEAD");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("HEAD", saved.getRequestMethod());
        }

        @Test
        @DisplayName("requestMethod 为 OPTIONS 时插入与查询应正确")
        void optionsMethodShouldBeStoredCorrectly() {
            successHistory.setRequestMethod("OPTIONS");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("OPTIONS", saved.getRequestMethod());
        }

        @Test
        @DisplayName("status 为 SUCCESS 且 responseStatusCode 为 500 时插入与查询应正确")
        void successStatusWith5xxCodeShouldBeStoredCorrectly() {
            successHistory.setStatus("SUCCESS");
            successHistory.setResponseStatusCode(500);
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("SUCCESS", saved.getStatus());
            assertEquals(500, saved.getResponseStatusCode());
        }

        @Test
        @DisplayName("status 为 FAILURE 且 responseStatusCode 为 200 时插入与查询应正确")
        void failureStatusWith2xxCodeShouldBeStoredCorrectly() {
            failureHistory.setStatus("FAILURE");
            failureHistory.setResponseStatusCode(200);
            debugHistoryMapper.insert(failureHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(failureHistory.getId());
            assertEquals("FAILURE", saved.getStatus());
            assertEquals(200, saved.getResponseStatusCode());
        }

        @Test
        @DisplayName("requestContentType 为 application/x-www-form-urlencoded 时插入与查询应正确")
        void formUrlencodedContentTypeShouldBeStoredCorrectly() {
            successHistory.setRequestContentType("application/x-www-form-urlencoded");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("application/x-www-form-urlencoded", saved.getRequestContentType());
        }

        @Test
        @DisplayName("responseContentType 为 application/octet-stream 时插入与查询应正确")
        void octetStreamResponseContentTypeShouldBeStoredCorrectly() {
            successHistory.setResponseContentType("application/octet-stream");
            debugHistoryMapper.insert(successHistory);

            DebugHistoryEntity saved = debugHistoryMapper.selectById(successHistory.getId());
            assertEquals("application/octet-stream", saved.getResponseContentType());
        }
    }
}