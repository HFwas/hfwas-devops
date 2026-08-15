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
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiDefinitionEntity 实体类测试
 * <p>
 * 验证 Lombok 注解、MyBatis-Plus 注解、字段赋值、JSON 序列化/反序列化
 * 以及 equals/hashCode/toString 等行为。
 *
 * @author hfwas
 */
@DisplayName("ApiDefinitionEntity — 接口定义实体测试")
class ApiDefinitionEntityTest {

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
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            assertNull(entity.getId());
            assertNull(entity.getProjectId());
            assertNull(entity.getGroupId());
            assertNull(entity.getName());
            assertNull(entity.getPath());
            assertNull(entity.getMethod());
            assertNull(entity.getStatus());
            assertNull(entity.getVersion());
            assertNull(entity.getTags());
            assertNull(entity.getDescription());
            assertNull(entity.getProtocol());
            assertNull(entity.getHost());
            assertNull(entity.getContentType());
            assertNull(entity.getDeleted());
            assertNull(entity.getCreateBy());
            assertNull(entity.getUpdateBy());
            assertNull(entity.getCreateTime());
            assertNull(entity.getUpdateTime());
        }

        @Test
        @DisplayName("setter/getter 应正确赋值和取值")
        void settersAndGettersShouldWorkCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setGroupId(3001L);
            entity.setName("获取用户列表");
            entity.setPath("/api/users/list");
            entity.setMethod("GET");
            entity.setStatus("PUBLISHED");
            entity.setVersion("1.0.0");
            entity.setTags(List.of("用户", "查询"));
            entity.setDescription("分页获取用户列表");
            entity.setProtocol("HTTP");
            entity.setHost("localhost:8080");
            entity.setContentType("application/json");
            entity.setDeleted(0);
            entity.setCreateBy(1L);
            entity.setUpdateBy(1L);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            assertEquals(1001L, entity.getId());
            assertEquals(2001L, entity.getProjectId());
            assertEquals(3001L, entity.getGroupId());
            assertEquals("获取用户列表", entity.getName());
            assertEquals("/api/users/list", entity.getPath());
            assertEquals("GET", entity.getMethod());
            assertEquals("PUBLISHED", entity.getStatus());
            assertEquals("1.0.0", entity.getVersion());
            assertEquals(List.of("用户", "查询"), entity.getTags());
            assertEquals("分页获取用户列表", entity.getDescription());
            assertEquals("HTTP", entity.getProtocol());
            assertEquals("localhost:8080", entity.getHost());
            assertEquals("application/json", entity.getContentType());
            assertEquals(0, entity.getDeleted());
            assertEquals(1L, entity.getCreateBy());
            assertEquals(1L, entity.getUpdateBy());
            assertEquals(now, entity.getCreateTime());
            assertEquals(now, entity.getUpdateTime());
        }

        @Test
        @DisplayName("method 应支持所有 HTTP 方法")
        void methodShouldSupportAllHttpMethods() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            String[] methods = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};
            for (String method : methods) {
                entity.setMethod(method);
                assertEquals(method, entity.getMethod());
            }
        }

        @Test
        @DisplayName("status 应支持 DRAFT/PUBLISHED/DEPRECATED")
        void statusShouldSupportEnumValues() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setStatus("DRAFT");
            assertEquals("DRAFT", entity.getStatus());

            entity.setStatus("PUBLISHED");
            assertEquals("PUBLISHED", entity.getStatus());

            entity.setStatus("DEPRECATED");
            assertEquals("DEPRECATED", entity.getStatus());
        }

        @Test
        @DisplayName("protocol 应支持 HTTP 和 HTTPS")
        void protocolShouldSupportHttpAndHttps() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setProtocol("HTTP");
            assertEquals("HTTP", entity.getProtocol());

            entity.setProtocol("HTTPS");
            assertEquals("HTTPS", entity.getProtocol());
        }

        @Test
        @DisplayName("tags 可为 null 或空列表")
        void tagsShouldAcceptNullOrEmpty() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setTags(null);
            assertNull(entity.getTags());

            entity.setTags(List.of());
            assertTrue(entity.getTags().isEmpty());

            entity.setTags(List.of("标签1", "标签2"));
            assertEquals(2, entity.getTags().size());
        }

        @Test
        @DisplayName("path 应支持含路径参数的模板")
        void pathShouldSupportPathParameters() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setPath("/api/users/{id}");
            assertEquals("/api/users/{id}", entity.getPath());

            entity.setPath("/api/users/{userId}/orders/{orderId}");
            assertEquals("/api/users/{userId}/orders/{orderId}", entity.getPath());
        }

        @Test
        @DisplayName("version 应支持语义化版本号")
        void versionShouldSupportSemanticVersions() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setVersion("1.0.0");
            assertEquals("1.0.0", entity.getVersion());

            entity.setVersion("2.3.1-beta");
            assertEquals("2.3.1-beta", entity.getVersion());

            entity.setVersion("0.0.1-SNAPSHOT");
            assertEquals("0.0.1-SNAPSHOT", entity.getVersion());
        }

        @Test
        @DisplayName("host 可为 null（未指定调试主机）")
        void hostShouldBeNullable() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setHost(null);
            assertNull(entity.getHost());

            entity.setHost("api.example.com");
            assertEquals("api.example.com", entity.getHost());
        }

        @Test
        @DisplayName("contentType 可为 null 或标准 MIME 类型")
        void contentTypeShouldAcceptNullOrStandardMimeTypes() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setContentType(null);
            assertNull(entity.getContentType());

            entity.setContentType("application/json");
            assertEquals("application/json", entity.getContentType());

            entity.setContentType("application/xml");
            assertEquals("application/xml", entity.getContentType());

            entity.setContentType("multipart/form-data");
            assertEquals("multipart/form-data", entity.getContentType());
        }

        @Test
        @DisplayName("description 可为 null 或空字符串")
        void descriptionShouldAcceptNullOrEmpty() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setDescription(null);
            assertNull(entity.getDescription());

            entity.setDescription("");
            assertEquals("", entity.getDescription());

            entity.setDescription("接口描述文本");
            assertEquals("接口描述文本", entity.getDescription());
        }

        @Test
        @DisplayName("deleted 应支持 0 和 1 两个值")
        void deletedShouldSupportZeroAndOne() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();

            entity.setDeleted(0);
            assertEquals(0, entity.getDeleted());

            entity.setDeleted(1);
            assertEquals(1, entity.getDeleted());
        }

        @Test
        @DisplayName("name 应支持较长字符串")
        void nameShouldSupportLongStrings() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            String longName = "A".repeat(100);
            entity.setName(longName);
            assertEquals(100, entity.getName().length());
            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("id 字段应支持 ASSIGN_ID 类型的 Long 值")
        void idShouldSupportLargeLongValues() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
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

            ApiDefinitionEntity entity1 = buildFullEntity(1001L, now);
            ApiDefinitionEntity entity2 = buildFullEntity(1001L, now);

            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }

        @Test
        @DisplayName("不同 id 的实体应不相等")
        void entitiesWithDifferentIdsShouldNotBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            ApiDefinitionEntity entity1 = buildFullEntity(1001L, now);
            ApiDefinitionEntity entity2 = buildFullEntity(1002L, now);

            assertNotEquals(entity1, entity2);
        }

        @Test
        @DisplayName("空实体应与自身相等")
        void emptyEntityShouldBeEqualToItself() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            assertEquals(entity, entity);
            assertEquals(entity.hashCode(), entity.hashCode());
        }

        @Test
        @DisplayName("实体不应与 null 相等")
        void entityShouldNotEqualNull() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            assertNotEquals(null, entity);
        }

        @Test
        @DisplayName("实体不应与其他类型相等")
        void entityShouldNotEqualDifferentType() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            assertNotEquals("string", entity);
        }

        @Test
        @DisplayName("null id 的两个空实体应相等")
        void twoNullIdEntitiesShouldBeEqual() {
            ApiDefinitionEntity entity1 = new ApiDefinitionEntity();
            ApiDefinitionEntity entity2 = new ApiDefinitionEntity();
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
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            String str = entity.toString();
            assertTrue(str.contains("ApiDefinitionEntity"));
        }

        @Test
        @DisplayName("toString 应包含非空字段值")
        void toStringShouldIncludeNonNullFieldValues() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            entity.setId(1001L);
            entity.setName("获取用户列表");
            entity.setMethod("GET");

            String str = entity.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("获取用户列表"));
            assertTrue(str.contains("GET"));
        }

        @Test
        @DisplayName("toString 应处理 null 字段不抛异常")
        void toStringShouldHandleNullFields() {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            String str = entity.toString();
            assertNotNull(str);
            assertTrue(str.contains("ApiDefinitionEntity"));
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
            ApiDefinitionEntity original = buildFullEntity(1001L, now);

            String json = objectMapper.writeValueAsString(original);
            ApiDefinitionEntity deserialized = objectMapper.readValue(json, ApiDefinitionEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("空实体序列化与反序列化应保持一致性")
        void emptyEntityShouldSerializeAndDeserialize() throws Exception {
            ApiDefinitionEntity original = new ApiDefinitionEntity();

            String json = objectMapper.writeValueAsString(original);
            ApiDefinitionEntity deserialized = objectMapper.readValue(json, ApiDefinitionEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("tags 列表应正确序列化与反序列化")
        void tagsShouldSerializeAndDeserialize() throws Exception {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            entity.setId(1001L);
            entity.setTags(List.of("用户", "查询", "分页"));

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"tags\":[\"用户\",\"查询\",\"分页\"]"));

            ApiDefinitionEntity deserialized = objectMapper.readValue(json, ApiDefinitionEntity.class);
            assertEquals(List.of("用户", "查询", "分页"), deserialized.getTags());
        }

        @Test
        @DisplayName("JSON 应包含所有非 null 字段")
        void jsonShouldContainNonNullFields() throws Exception {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("获取用户列表");
            entity.setMethod("GET");
            entity.setPath("/api/users/list");
            entity.setStatus("PUBLISHED");
            entity.setVersion("1.0.0");

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"id\":1001"));
            assertTrue(json.contains("\"projectId\":2001"));
            assertTrue(json.contains("\"name\":\"获取用户列表\""));
            assertTrue(json.contains("\"method\":\"GET\""));
            assertTrue(json.contains("\"path\":\"/api/users/list\""));
            assertTrue(json.contains("\"status\":\"PUBLISHED\""));
            assertTrue(json.contains("\"version\":\"1.0.0\""));
        }

        @Test
        @DisplayName("JSON 不应包含 null 字段")
        void jsonShouldNotContainNullFields() throws Exception {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            entity.setId(1001L);

            String json = objectMapper.writeValueAsString(entity);
            assertFalse(json.contains("\"createTime\""));
            assertFalse(json.contains("\"updateTime\""));
            assertFalse(json.contains("\"deleted\""));
            assertFalse(json.contains("\"tags\""));
        }
    }

    // ========================================================================
    // MyBatis-Plus 注解验证
    // ========================================================================

    @Nested
    @DisplayName("MyBatis-Plus 注解验证")
    class MyBatisPlusAnnotations {

        @Test
        @DisplayName("实体应标注 @TableName(\"api_definition\") 且 autoResultMap = true")
        void shouldHaveTableNameAnnotation() {
            TableName annotation = ApiDefinitionEntity.class.getAnnotation(TableName.class);
            assertNotNull(annotation);
            assertEquals("api_definition", annotation.value());
            assertTrue(annotation.autoResultMap());
        }

        @Test
        @DisplayName("id 字段应标注 @TableId 且类型为 ASSIGN_ID")
        void idFieldShouldHaveTableIdAnnotation() throws Exception {
            java.lang.reflect.Field idField = ApiDefinitionEntity.class.getDeclaredField("id");
            TableId annotation = idField.getAnnotation(TableId.class);
            assertNotNull(annotation);
            assertEquals(IdType.ASSIGN_ID, annotation.type());
        }

        @Test
        @DisplayName("deleted 字段应标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws Exception {
            java.lang.reflect.Field deletedField = ApiDefinitionEntity.class.getDeclaredField("deleted");
            TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("tags 字段应标注 @TableField(typeHandler = JacksonTypeHandler.class)")
        void tagsFieldShouldHaveJacksonTypeHandler() throws Exception {
            java.lang.reflect.Field tagsField = ApiDefinitionEntity.class.getDeclaredField("tags");
            TableField annotation = tagsField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals("com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler",
                    annotation.typeHandler().getName());
        }

        @Test
        @DisplayName("createBy 字段应标注 @TableField(fill = INSERT)")
        void createByFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createByField = ApiDefinitionEntity.class.getDeclaredField("createBy");
            TableField annotation = createByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateBy 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateByFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateByField = ApiDefinitionEntity.class.getDeclaredField("updateBy");
            TableField annotation = updateByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }

        @Test
        @DisplayName("createTime 字段应标注 @TableField(fill = INSERT)")
        void createTimeFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createTimeField = ApiDefinitionEntity.class.getDeclaredField("createTime");
            TableField annotation = createTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateTime 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateTimeFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateTimeField = ApiDefinitionEntity.class.getDeclaredField("updateTime");
            TableField annotation = updateTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private ApiDefinitionEntity buildFullEntity(Long id, LocalDateTime now) {
        ApiDefinitionEntity entity = new ApiDefinitionEntity();
        entity.setId(id);
        entity.setProjectId(2001L);
        entity.setGroupId(3001L);
        entity.setName("获取用户列表");
        entity.setPath("/api/users/list");
        entity.setMethod("GET");
        entity.setStatus("PUBLISHED");
        entity.setVersion("1.0.0");
        entity.setTags(List.of("用户", "查询"));
        entity.setDescription("分页获取用户列表");
        entity.setProtocol("HTTP");
        entity.setHost("localhost:8080");
        entity.setContentType("application/json");
        entity.setDeleted(0);
        entity.setCreateBy(1L);
        entity.setUpdateBy(1L);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}