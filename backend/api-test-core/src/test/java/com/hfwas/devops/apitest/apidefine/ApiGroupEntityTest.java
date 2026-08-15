package com.hfwas.devops.apitest.apidefine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiGroupEntity 实体类测试
 * <p>
 * 验证 Lombok 注解、MyBatis-Plus 注解、字段赋值、JSON 序列化/反序列化
 * 以及 equals/hashCode/toString 等行为。
 *
 * @author hfwas
 */
@DisplayName("ApiGroupEntity — 接口分组实体测试")
class ApiGroupEntityTest {

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
            ApiGroupEntity entity = new ApiGroupEntity();
            assertNull(entity.getId());
            assertNull(entity.getProjectId());
            assertNull(entity.getParentId());
            assertNull(entity.getName());
            assertNull(entity.getDescription());
            assertNull(entity.getSortOrder());
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
            ApiGroupEntity entity = new ApiGroupEntity();

            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setParentId(3001L);
            entity.setName("用户管理");
            entity.setSortOrder(1);
            entity.setDescription("用户相关接口分组");
            entity.setDeleted(0);
            entity.setCreateBy(1L);
            entity.setUpdateBy(1L);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            assertEquals(1001L, entity.getId());
            assertEquals(2001L, entity.getProjectId());
            assertEquals(3001L, entity.getParentId());
            assertEquals("用户管理", entity.getName());
            assertEquals(1, entity.getSortOrder());
            assertEquals("用户相关接口分组", entity.getDescription());
            assertEquals(0, entity.getDeleted());
            assertEquals(1L, entity.getCreateBy());
            assertEquals(1L, entity.getUpdateBy());
            assertEquals(now, entity.getCreateTime());
            assertEquals(now, entity.getUpdateTime());
        }

        @Test
        @DisplayName("parentId 应为 null 可空（根级分组）")
        void parentIdShouldBeNullable() {
            ApiGroupEntity entity = new ApiGroupEntity();
            entity.setParentId(null);
            assertNull(entity.getParentId());

            entity.setParentId(5001L);
            assertEquals(5001L, entity.getParentId());
        }

        @Test
        @DisplayName("description 可为 null 或空字符串")
        void descriptionShouldAcceptNullOrEmpty() {
            ApiGroupEntity entity = new ApiGroupEntity();

            entity.setDescription(null);
            assertNull(entity.getDescription());

            entity.setDescription("");
            assertEquals("", entity.getDescription());

            entity.setDescription("分组描述");
            assertEquals("分组描述", entity.getDescription());
        }

        @Test
        @DisplayName("sortOrder 应支持正数、零和负数")
        void sortOrderShouldSupportVariousValues() {
            ApiGroupEntity entity = new ApiGroupEntity();

            entity.setSortOrder(0);
            assertEquals(0, entity.getSortOrder());

            entity.setSortOrder(999);
            assertEquals(999, entity.getSortOrder());

            entity.setSortOrder(-1);
            assertEquals(-1, entity.getSortOrder());
        }

        @Test
        @DisplayName("deleted 应支持 0 和 1 两个值")
        void deletedShouldSupportZeroAndOne() {
            ApiGroupEntity entity = new ApiGroupEntity();

            entity.setDeleted(0);
            assertEquals(0, entity.getDeleted());

            entity.setDeleted(1);
            assertEquals(1, entity.getDeleted());
        }

        @Test
        @DisplayName("name 应支持较长字符串")
        void nameShouldSupportLongStrings() {
            ApiGroupEntity entity = new ApiGroupEntity();
            String longName = "A".repeat(100);
            entity.setName(longName);
            assertEquals(100, entity.getName().length());
            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("id 字段应支持 ASSIGN_ID 类型的 Long 值")
        void idShouldSupportLargeLongValues() {
            ApiGroupEntity entity = new ApiGroupEntity();
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

            ApiGroupEntity entity1 = buildFullEntity(1001L, now);
            ApiGroupEntity entity2 = buildFullEntity(1001L, now);

            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }

        @Test
        @DisplayName("不同 id 的实体应不相等")
        void entitiesWithDifferentIdsShouldNotBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            ApiGroupEntity entity1 = buildFullEntity(1001L, now);
            ApiGroupEntity entity2 = buildFullEntity(1002L, now);

            assertNotEquals(entity1, entity2);
        }

        @Test
        @DisplayName("空实体应与自身相等")
        void emptyEntityShouldBeEqualToItself() {
            ApiGroupEntity entity = new ApiGroupEntity();
            assertEquals(entity, entity);
            assertEquals(entity.hashCode(), entity.hashCode());
        }

        @Test
        @DisplayName("实体不应与 null 相等")
        void entityShouldNotEqualNull() {
            ApiGroupEntity entity = new ApiGroupEntity();
            assertNotEquals(null, entity);
        }

        @Test
        @DisplayName("实体不应与其他类型相等")
        void entityShouldNotEqualDifferentType() {
            ApiGroupEntity entity = new ApiGroupEntity();
            assertNotEquals("string", entity);
        }

        @Test
        @DisplayName("null id 的两个空实体应相等")
        void twoNullIdEntitiesShouldBeEqual() {
            ApiGroupEntity entity1 = new ApiGroupEntity();
            ApiGroupEntity entity2 = new ApiGroupEntity();
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
            ApiGroupEntity entity = new ApiGroupEntity();
            String str = entity.toString();
            assertTrue(str.contains("ApiGroupEntity"));
        }

        @Test
        @DisplayName("toString 应包含非空字段值")
        void toStringShouldIncludeNonNullFieldValues() {
            ApiGroupEntity entity = new ApiGroupEntity();
            entity.setId(1001L);
            entity.setName("用户管理");

            String str = entity.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("用户管理"));
        }

        @Test
        @DisplayName("toString 不应包含 null 值的字段名或仅显示 null")
        void toStringShouldHandleNullFields() {
            ApiGroupEntity entity = new ApiGroupEntity();
            String str = entity.toString();
            assertNotNull(str);
            assertTrue(str.contains("ApiGroupEntity"));
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
            ApiGroupEntity original = buildFullEntity(1001L, now);

            String json = objectMapper.writeValueAsString(original);
            ApiGroupEntity deserialized = objectMapper.readValue(json, ApiGroupEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("空实体序列化与反序列化应保持一致性")
        void emptyEntityShouldSerializeAndDeserialize() throws Exception {
            ApiGroupEntity original = new ApiGroupEntity();

            String json = objectMapper.writeValueAsString(original);
            ApiGroupEntity deserialized = objectMapper.readValue(json, ApiGroupEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("JSON 应包含所有非 null 字段")
        void jsonShouldContainNonNullFields() throws Exception {
            ApiGroupEntity entity = new ApiGroupEntity();
            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("用户管理");
            entity.setSortOrder(1);

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"id\":1001"));
            assertTrue(json.contains("\"projectId\":2001"));
            assertTrue(json.contains("\"name\":\"用户管理\""));
            assertTrue(json.contains("\"sortOrder\":1"));
        }

        @Test
        @DisplayName("JSON 不应包含 null 字段")
        void jsonShouldNotContainNullFields() throws Exception {
            ApiGroupEntity entity = new ApiGroupEntity();
            entity.setId(1001L);

            String json = objectMapper.writeValueAsString(entity);
            assertFalse(json.contains("\"createTime\""));
            assertFalse(json.contains("\"updateTime\""));
            assertFalse(json.contains("\"deleted\""));
        }
    }

    // ========================================================================
    // MyBatis-Plus 注解验证
    // ========================================================================

    @Nested
    @DisplayName("MyBatis-Plus 注解验证")
    class MyBatisPlusAnnotations {

        @Test
        @DisplayName("实体应标注 @TableName(\"api_group\")")
        void shouldHaveTableNameAnnotation() {
            TableName annotation = ApiGroupEntity.class.getAnnotation(TableName.class);
            assertNotNull(annotation);
            assertEquals("api_group", annotation.value());
        }

        @Test
        @DisplayName("id 字段应标注 @TableId 且类型为 ASSIGN_ID")
        void idFieldShouldHaveTableIdAnnotation() throws Exception {
            java.lang.reflect.Field idField = ApiGroupEntity.class.getDeclaredField("id");
            TableId annotation = idField.getAnnotation(TableId.class);
            assertNotNull(annotation);
            assertEquals(IdType.ASSIGN_ID, annotation.type());
        }

        @Test
        @DisplayName("deleted 字段应标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws Exception {
            java.lang.reflect.Field deletedField = ApiGroupEntity.class.getDeclaredField("deleted");
            TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("createBy 字段应标注 @TableField(fill = INSERT)")
        void createByFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createByField = ApiGroupEntity.class.getDeclaredField("createBy");
            TableField annotation = createByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateBy 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateByFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateByField = ApiGroupEntity.class.getDeclaredField("updateBy");
            TableField annotation = updateByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }

        @Test
        @DisplayName("createTime 字段应标注 @TableField(fill = INSERT)")
        void createTimeFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createTimeField = ApiGroupEntity.class.getDeclaredField("createTime");
            TableField annotation = createTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateTime 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateTimeFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateTimeField = ApiGroupEntity.class.getDeclaredField("updateTime");
            TableField annotation = updateTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private ApiGroupEntity buildFullEntity(Long id, LocalDateTime now) {
        ApiGroupEntity entity = new ApiGroupEntity();
        entity.setId(id);
        entity.setProjectId(2001L);
        entity.setParentId(3001L);
        entity.setName("用户管理");
        entity.setSortOrder(1);
        entity.setDescription("用户相关接口分组");
        entity.setDeleted(0);
        entity.setCreateBy(1L);
        entity.setUpdateBy(1L);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}