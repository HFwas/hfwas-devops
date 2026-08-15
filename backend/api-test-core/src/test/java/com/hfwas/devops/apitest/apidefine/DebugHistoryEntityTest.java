package com.hfwas.devops.apitest.apidefine;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hfwas.devops.apitest.history.entity.DebugHistoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DebugHistoryEntity 实体类测试
 * <p>
 * 验证 Lombok 注解、MyBatis-Plus 注解、字段赋值、JSON 序列化/反序列化
 * 以及 equals/hashCode/toString 等行为。
 *
 * @author hfwas
 */
@DisplayName("DebugHistoryEntity — 调试历史记录实体测试")
class DebugHistoryEntityTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ========================================================================
    // 实体创建与基本字段赋值
    // ========================================================================

    @Nested
    @DisplayName("实体创建与字段赋值")
    class EntityCreationAndFieldAssignment {

        @Test
        @DisplayName("无参构造应创建空实体，所有字段为默认值")
        void noArgsConstructorShouldCreateEmptyEntity() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            assertNull(entity.getId());
            assertNull(entity.getProjectId());
            assertNull(entity.getDefinitionId());
            assertNull(entity.getEnvironmentId());
            assertNull(entity.getName());
            assertNull(entity.getRequestUrl());
            assertNull(entity.getRequestMethod());
            assertNull(entity.getRequestHeaders());
            assertNull(entity.getRequestQuery());
            assertNull(entity.getRequestBody());
            assertNull(entity.getRequestContentType());
            assertNull(entity.getResponseStatusCode());
            assertNull(entity.getResponseHeaders());
            assertNull(entity.getResponseBody());
            assertNull(entity.getResponseContentType());
            assertNull(entity.getResponseSize());
            assertNull(entity.getDurationMs());
            assertNull(entity.getStatus());
            assertNull(entity.getErrorMessage());
            assertNull(entity.getAssertionResults());
            assertNull(entity.getAllAssertionsPassed());
            assertNull(entity.getExtractedVariables());
            assertNull(entity.getDeleted());
            assertNull(entity.getCreateBy());
            assertNull(entity.getCreateTime());
        }

        @Test
        @DisplayName("setter/getter 应正确赋值和取值")
        void settersAndGettersShouldWorkCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            DebugHistoryEntity entity = buildFullEntity(1001L, now);

            assertEquals(1001L, entity.getId());
            assertEquals(2001L, entity.getProjectId());
            assertEquals(3001L, entity.getDefinitionId());
            assertEquals(4001L, entity.getEnvironmentId());
            assertEquals("获取用户列表调试", entity.getName());
            assertEquals("http://localhost:8080/api/users/list", entity.getRequestUrl());
            assertEquals("GET", entity.getRequestMethod());
            assertEquals(2, entity.getRequestHeaders().size());
            assertEquals("application/json", entity.getRequestHeaders().get("Content-Type"));
            assertEquals(1, entity.getRequestQuery().size());
            assertEquals("1", entity.getRequestQuery().get("page"));
            assertEquals("{}", entity.getRequestBody());
            assertEquals("application/json", entity.getRequestContentType());
            assertEquals(200, entity.getResponseStatusCode());
            assertEquals(2, entity.getResponseHeaders().size());
            assertEquals("application/json", entity.getResponseHeaders().get("Content-Type"));
            assertEquals("{\"code\":200,\"data\":[]}", entity.getResponseBody());
            assertEquals("application/json", entity.getResponseContentType());
            assertEquals(256L, entity.getResponseSize());
            assertEquals(150L, entity.getDurationMs());
            assertEquals("SUCCESS", entity.getStatus());
            assertNull(entity.getErrorMessage());
            assertEquals(1, entity.getAssertionResults().size());
            assertEquals(1, entity.getAllAssertionsPassed());
            assertEquals(1, entity.getExtractedVariables().size());
            assertEquals("1", entity.getExtractedVariables().get("userId"));
            assertEquals(0, entity.getDeleted());
            assertEquals(1L, entity.getCreateBy());
            assertEquals(now, entity.getCreateTime());
        }

        @Test
        @DisplayName("status 应支持 SUCCESS / FAILURE / ERROR")
        void statusShouldSupportEnumValues() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setStatus("SUCCESS");
            assertEquals("SUCCESS", entity.getStatus());

            entity.setStatus("FAILURE");
            assertEquals("FAILURE", entity.getStatus());

            entity.setStatus("ERROR");
            assertEquals("ERROR", entity.getStatus());
        }

        @Test
        @DisplayName("requestMethod 应支持所有 HTTP 方法")
        void requestMethodShouldSupportAllHttpMethods() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            String[] methods = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};
            for (String method : methods) {
                entity.setRequestMethod(method);
                assertEquals(method, entity.getRequestMethod());
            }
        }

        @Test
        @DisplayName("responseStatusCode 应支持常见 HTTP 状态码")
        void responseStatusCodeShouldSupportCommonCodes() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            int[] codes = {200, 201, 204, 301, 400, 401, 403, 404, 500, 502, 503};
            for (int code : codes) {
                entity.setResponseStatusCode(code);
                assertEquals(code, entity.getResponseStatusCode());
            }
        }

        @Test
        @DisplayName("responseStatusCode 可为 null（未收到响应）")
        void responseStatusCodeShouldBeNullable() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            assertNull(entity.getResponseStatusCode());
            entity.setResponseStatusCode(500);
            assertEquals(500, entity.getResponseStatusCode());
        }

        @Test
        @DisplayName("durationMs 应支持零和正数")
        void durationMsShouldSupportZeroAndPositive() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setDurationMs(0L);
            assertEquals(0L, entity.getDurationMs());

            entity.setDurationMs(9999L);
            assertEquals(9999L, entity.getDurationMs());

            entity.setDurationMs(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, entity.getDurationMs());
        }

        @Test
        @DisplayName("responseSize 应支持零和正数")
        void responseSizeShouldSupportZeroAndPositive() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setResponseSize(0L);
            assertEquals(0L, entity.getResponseSize());

            entity.setResponseSize(1048576L);
            assertEquals(1048576L, entity.getResponseSize());
        }

        @Test
        @DisplayName("requestHeaders 应支持 Map 的增删改")
        void requestHeadersShouldSupportMapOperations() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer token123");

            entity.setRequestHeaders(headers);
            assertEquals(2, entity.getRequestHeaders().size());
            assertEquals("application/json", entity.getRequestHeaders().get("Content-Type"));
            assertEquals("Bearer token123", entity.getRequestHeaders().get("Authorization"));

            entity.getRequestHeaders().put("Accept", "application/json");
            assertEquals(3, entity.getRequestHeaders().size());
        }

        @Test
        @DisplayName("requestHeaders 和 requestQuery 可为 null 或空 Map")
        void mapFieldsShouldAcceptNullOrEmpty() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setRequestHeaders(null);
            assertNull(entity.getRequestHeaders());

            entity.setRequestHeaders(new HashMap<>());
            assertTrue(entity.getRequestHeaders().isEmpty());

            entity.setRequestQuery(null);
            assertNull(entity.getRequestQuery());

            entity.setRequestQuery(new HashMap<>());
            assertTrue(entity.getRequestQuery().isEmpty());
        }

        @Test
        @DisplayName("responseHeaders 可为 null 或空 Map")
        void responseHeadersShouldAcceptNullOrEmpty() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setResponseHeaders(null);
            assertNull(entity.getResponseHeaders());

            entity.setResponseHeaders(new HashMap<>());
            assertTrue(entity.getResponseHeaders().isEmpty());
        }

        @Test
        @DisplayName("assertionResults 可为 null 或空列表")
        void assertionResultsShouldAcceptNullOrEmpty() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setAssertionResults(null);
            assertNull(entity.getAssertionResults());

            entity.setAssertionResults(List.of());
            assertTrue(entity.getAssertionResults().isEmpty());
        }

        @Test
        @DisplayName("extractedVariables 可为 null 或空 Map")
        void extractedVariablesShouldAcceptNullOrEmpty() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setExtractedVariables(null);
            assertNull(entity.getExtractedVariables());

            entity.setExtractedVariables(new HashMap<>());
            assertTrue(entity.getExtractedVariables().isEmpty());
        }

        @Test
        @DisplayName("allAssertionsPassed 应支持 0 和 1")
        void allAssertionsPassedShouldSupportZeroAndOne() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setAllAssertionsPassed(0);
            assertEquals(0, entity.getAllAssertionsPassed());

            entity.setAllAssertionsPassed(1);
            assertEquals(1, entity.getAllAssertionsPassed());
        }

        @Test
        @DisplayName("errorMessage 可为 null 或字符串")
        void errorMessageShouldAcceptNullOrString() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            assertNull(entity.getErrorMessage());

            entity.setErrorMessage("连接超时");
            assertEquals("连接超时", entity.getErrorMessage());

            entity.setErrorMessage("Internal Server Error");
            assertEquals("Internal Server Error", entity.getErrorMessage());
        }

        @Test
        @DisplayName("requestBody 和 responseBody 可为 null 或空字符串")
        void bodyFieldsShouldAcceptNullOrEmpty() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setRequestBody(null);
            assertNull(entity.getRequestBody());

            entity.setRequestBody("");
            assertEquals("", entity.getRequestBody());

            entity.setRequestBody("{\"key\":\"value\"}");
            assertEquals("{\"key\":\"value\"}", entity.getRequestBody());

            entity.setResponseBody(null);
            assertNull(entity.getResponseBody());

            entity.setResponseBody("{\"code\":200}");
            assertEquals("{\"code\":200}", entity.getResponseBody());
        }

        @Test
        @DisplayName("requestContentType 和 responseContentType 可为 null 或标准 MIME 类型")
        void contentTypeFieldsShouldAcceptNullOrStandardMimeTypes() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setRequestContentType(null);
            assertNull(entity.getRequestContentType());

            entity.setRequestContentType("application/json");
            assertEquals("application/json", entity.getRequestContentType());

            entity.setRequestContentType("application/xml");
            assertEquals("application/xml", entity.getRequestContentType());

            entity.setResponseContentType(null);
            assertNull(entity.getResponseContentType());

            entity.setResponseContentType("text/plain");
            assertEquals("text/plain", entity.getResponseContentType());
        }

        @Test
        @DisplayName("requestUrl 应支持带查询参数的 URL")
        void requestUrlShouldSupportQueryParameters() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setRequestUrl("http://localhost:8080/api/users?page=1&size=10");
            assertEquals("http://localhost:8080/api/users?page=1&size=10", entity.getRequestUrl());

            entity.setRequestUrl("/api/users/1001");
            assertEquals("/api/users/1001", entity.getRequestUrl());
        }

        @Test
        @DisplayName("deleted 应支持 0 和 1 两个值")
        void deletedShouldSupportZeroAndOne() {
            DebugHistoryEntity entity = new DebugHistoryEntity();

            entity.setDeleted(0);
            assertEquals(0, entity.getDeleted());

            entity.setDeleted(1);
            assertEquals(1, entity.getDeleted());
        }

        @Test
        @DisplayName("name 应支持较长字符串")
        void nameShouldSupportLongStrings() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            String longName = "A".repeat(100);
            entity.setName(longName);
            assertEquals(100, entity.getName().length());
            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("id 字段应支持 ASSIGN_ID 类型的 Long 值")
        void idShouldSupportLargeLongValues() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, entity.getId());

            entity.setId(1L);
            assertEquals(1L, entity.getId());
        }
    }

    // ========================================================================
    // equals 和 hashCode
    // ========================================================================

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同字段值的实体应相等")
        void entitiesWithSameValuesShouldBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            DebugHistoryEntity entity1 = buildFullEntity(1001L, now);
            DebugHistoryEntity entity2 = buildFullEntity(1001L, now);

            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }

        @Test
        @DisplayName("不同 id 的实体应不相等")
        void entitiesWithDifferentIdsShouldNotBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            DebugHistoryEntity entity1 = buildFullEntity(1001L, now);
            DebugHistoryEntity entity2 = buildFullEntity(1002L, now);

            assertNotEquals(entity1, entity2);
        }

        @Test
        @DisplayName("空实体应与自身相等")
        void emptyEntityShouldBeEqualToItself() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            assertEquals(entity, entity);
            assertEquals(entity.hashCode(), entity.hashCode());
        }

        @Test
        @DisplayName("实体不应与 null 相等")
        void entityShouldNotEqualNull() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            assertNotEquals(null, entity);
        }

        @Test
        @DisplayName("实体不应与其他类型相等")
        void entityShouldNotEqualDifferentType() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            assertNotEquals("string", entity);
        }

        @Test
        @DisplayName("null id 的两个空实体应相等")
        void twoNullIdEntitiesShouldBeEqual() {
            DebugHistoryEntity entity1 = new DebugHistoryEntity();
            DebugHistoryEntity entity2 = new DebugHistoryEntity();
            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }
    }

    // ========================================================================
    // toString
    // ========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringMethod {

        @Test
        @DisplayName("toString 应包含实体类名")
        void toStringShouldContainClassName() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            String str = entity.toString();
            assertTrue(str.contains("DebugHistoryEntity"));
        }

        @Test
        @DisplayName("toString 应包含非空字段值")
        void toStringShouldIncludeNonNullFieldValues() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);
            entity.setName("获取用户列表调试");
            entity.setRequestMethod("GET");
            entity.setResponseStatusCode(200);
            entity.setStatus("SUCCESS");
            entity.setDurationMs(150L);

            String str = entity.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("获取用户列表调试"));
            assertTrue(str.contains("GET"));
            assertTrue(str.contains("200"));
            assertTrue(str.contains("SUCCESS"));
            assertTrue(str.contains("150"));
        }

        @Test
        @DisplayName("toString 应处理 null 字段不抛异常")
        void toStringShouldHandleNullFields() {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            String str = entity.toString();
            assertNotNull(str);
            assertTrue(str.contains("DebugHistoryEntity"));
        }
    }

    // ========================================================================
    // JSON 序列化与反序列化
    // ========================================================================

    @Nested
    @DisplayName("JSON 序列化与反序列化")
    class JsonSerialization {

        @Test
        @DisplayName("实体应能序列化为 JSON 并反序列化回原对象")
        void shouldSerializeAndDeserialize() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            DebugHistoryEntity original = buildFullEntity(1001L, now);

            String json = objectMapper.writeValueAsString(original);
            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("空实体序列化与反序列化应保持一致性")
        void emptyEntityShouldSerializeAndDeserialize() throws Exception {
            DebugHistoryEntity original = new DebugHistoryEntity();

            String json = objectMapper.writeValueAsString(original);
            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("Map 类型字段应正确序列化与反序列化")
        void mapFieldsShouldSerializeAndDeserialize() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer token123");
            entity.setRequestHeaders(headers);

            Map<String, String> query = new HashMap<>();
            query.put("page", "1");
            query.put("size", "10");
            entity.setRequestQuery(query);

            String json = objectMapper.writeValueAsString(entity);
            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);

            assertEquals(entity.getRequestHeaders(), deserialized.getRequestHeaders());
            assertEquals(entity.getRequestQuery(), deserialized.getRequestQuery());
        }

        @Test
        @DisplayName("assertionResults 列表应正确序列化与反序列化")
        void assertionResultsShouldSerializeAndDeserialize() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);

            Map<String, Object> assertion1 = new HashMap<>();
            assertion1.put("name", "状态码校验");
            assertion1.put("passed", true);
            assertion1.put("actual", 200);
            assertion1.put("expected", 200);

            entity.setAssertionResults(List.of(assertion1));

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("状态码校验"));
            assertTrue(json.contains("true"));

            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);
            assertEquals(1, deserialized.getAssertionResults().size());
            assertEquals(true, deserialized.getAssertionResults().get(0).get("passed"));
        }

        @Test
        @DisplayName("extractedVariables 应正确序列化与反序列化")
        void extractedVariablesShouldSerializeAndDeserialize() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);

            Map<String, String> variables = new HashMap<>();
            variables.put("userId", "1001");
            variables.put("token", "abc123");
            entity.setExtractedVariables(variables);

            String json = objectMapper.writeValueAsString(entity);
            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);

            assertEquals(entity.getExtractedVariables(), deserialized.getExtractedVariables());
        }

        @Test
        @DisplayName("responseHeaders 应正确序列化与反序列化")
        void responseHeadersShouldSerializeAndDeserialize() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json;charset=UTF-8");
            headers.put("X-Request-Id", "req-abc-123");
            entity.setResponseHeaders(headers);

            String json = objectMapper.writeValueAsString(entity);
            DebugHistoryEntity deserialized = objectMapper.readValue(json, DebugHistoryEntity.class);

            assertEquals(entity.getResponseHeaders(), deserialized.getResponseHeaders());
        }

        @Test
        @DisplayName("JSON 应包含所有非 null 字段")
        void jsonShouldContainNonNullFields() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setDefinitionId(3001L);
            entity.setName("获取用户列表调试");
            entity.setRequestUrl("http://localhost:8080/api/users/list");
            entity.setRequestMethod("GET");
            entity.setResponseStatusCode(200);
            entity.setStatus("SUCCESS");
            entity.setDurationMs(150L);

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"id\":1001"));
            assertTrue(json.contains("\"projectId\":2001"));
            assertTrue(json.contains("\"definitionId\":3001"));
            assertTrue(json.contains("\"name\":\"获取用户列表调试\""));
            assertTrue(json.contains("\"requestUrl\":\"http://localhost:8080/api/users/list\""));
            assertTrue(json.contains("\"requestMethod\":\"GET\""));
            assertTrue(json.contains("\"responseStatusCode\":200"));
            assertTrue(json.contains("\"status\":\"SUCCESS\""));
            assertTrue(json.contains("\"durationMs\":150"));
        }

        @Test
        @DisplayName("JSON 不应包含 null 字段")
        void jsonShouldNotContainNullFields() throws Exception {
            DebugHistoryEntity entity = new DebugHistoryEntity();
            entity.setId(1001L);

            String json = objectMapper.writeValueAsString(entity);
            assertFalse(json.contains("\"createTime\""));
            assertFalse(json.contains("\"deleted\""));
            assertFalse(json.contains("\"requestHeaders\""));
            assertFalse(json.contains("\"requestQuery\""));
            assertFalse(json.contains("\"responseHeaders\""));
            assertFalse(json.contains("\"assertionResults\""));
            assertFalse(json.contains("\"extractedVariables\""));
        }
    }

    // ========================================================================
    // MyBatis-Plus 注解验证
    // ========================================================================

    @Nested
    @DisplayName("MyBatis-Plus 注解验证")
    class MyBatisPlusAnnotations {

        @Test
        @DisplayName("实体应标注 @TableName(\"api_debug_history\") 且 autoResultMap = true")
        void shouldHaveTableNameAnnotation() {
            TableName annotation = DebugHistoryEntity.class.getAnnotation(TableName.class);
            assertNotNull(annotation);
            assertEquals("api_debug_history", annotation.value());
            assertTrue(annotation.autoResultMap());
        }

        @Test
        @DisplayName("id 字段应标注 @TableId 且类型为 ASSIGN_ID")
        void idFieldShouldHaveTableIdAnnotation() throws Exception {
            java.lang.reflect.Field idField = DebugHistoryEntity.class.getDeclaredField("id");
            TableId annotation = idField.getAnnotation(TableId.class);
            assertNotNull(annotation);
            assertEquals(IdType.ASSIGN_ID, annotation.type());
        }

        @Test
        @DisplayName("deleted 字段应标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws Exception {
            java.lang.reflect.Field deletedField = DebugHistoryEntity.class.getDeclaredField("deleted");
            TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("requestHeaders 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void requestHeadersFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("requestHeaders");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("requestQuery 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void requestQueryFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("requestQuery");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("responseHeaders 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void responseHeadersFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("responseHeaders");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("assertionResults 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void assertionResultsFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("assertionResults");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("extractedVariables 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void extractedVariablesFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("extractedVariables");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("createBy 字段应标注 @TableField(fill = INSERT)")
        void createByFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("createBy");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("createTime 字段应标注 @TableField(fill = INSERT)")
        void createTimeFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field field = DebugHistoryEntity.class.getDeclaredField("createTime");
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private DebugHistoryEntity buildFullEntity(Long id, LocalDateTime now) {
        DebugHistoryEntity entity = new DebugHistoryEntity();
        entity.setId(id);
        entity.setProjectId(2001L);
        entity.setDefinitionId(3001L);
        entity.setEnvironmentId(4001L);
        entity.setName("获取用户列表调试");
        entity.setRequestUrl("http://localhost:8080/api/users/list");
        entity.setRequestMethod("GET");

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Authorization", "Bearer token123");
        entity.setRequestHeaders(requestHeaders);

        Map<String, String> requestQuery = new HashMap<>();
        requestQuery.put("page", "1");
        entity.setRequestQuery(requestQuery);

        entity.setRequestBody("{}");
        entity.setRequestContentType("application/json");
        entity.setResponseStatusCode(200);

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        responseHeaders.put("X-Request-Id", "req-abc-123");
        entity.setResponseHeaders(responseHeaders);

        entity.setResponseBody("{\"code\":200,\"data\":[]}");
        entity.setResponseContentType("application/json");
        entity.setResponseSize(256L);
        entity.setDurationMs(150L);
        entity.setStatus("SUCCESS");

        Map<String, Object> assertion1 = new HashMap<>();
        assertion1.put("name", "状态码校验");
        assertion1.put("passed", true);
        assertion1.put("actual", 200);
        assertion1.put("expected", 200);
        entity.setAssertionResults(List.of(assertion1));

        entity.setAllAssertionsPassed(1);

        Map<String, String> extractedVariables = new HashMap<>();
        extractedVariables.put("userId", "1");
        entity.setExtractedVariables(extractedVariables);

        entity.setDeleted(0);
        entity.setCreateBy(1L);
        entity.setCreateTime(now);
        return entity;
    }
}