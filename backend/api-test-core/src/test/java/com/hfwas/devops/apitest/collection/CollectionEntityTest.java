package com.hfwas.devops.apitest.collection;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollectionEntity 实体类测试
 * <p>
 * 验证 Lombok 注解、MyBatis-Plus 注解、字段赋值、JSON 序列化/反序列化
 * 以及 equals/hashCode/toString 等行为。
 *
 * @author hfwas
 */
@DisplayName("CollectionEntity — 集合实体测试")
class CollectionEntityTest {

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
            CollectionEntity entity = new CollectionEntity();
            assertNull(entity.getId());
            assertNull(entity.getProjectId());
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
            CollectionEntity entity = new CollectionEntity();

            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("用户管理接口集合");
            entity.setDescription("包含用户管理模块的所有接口");
            entity.setSortOrder(1);
            entity.setDeleted(0);
            entity.setCreateBy(1L);
            entity.setUpdateBy(1L);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            assertEquals(1001L, entity.getId());
            assertEquals(2001L, entity.getProjectId());
            assertEquals("用户管理接口集合", entity.getName());
            assertEquals("包含用户管理模块的所有接口", entity.getDescription());
            assertEquals(1, entity.getSortOrder());
            assertEquals(0, entity.getDeleted());
            assertEquals(1L, entity.getCreateBy());
            assertEquals(1L, entity.getUpdateBy());
            assertEquals(now, entity.getCreateTime());
            assertEquals(now, entity.getUpdateTime());
        }

        @Test
        @DisplayName("name 应支持较长字符串")
        void nameShouldSupportLongStrings() {
            CollectionEntity entity = new CollectionEntity();
            String longName = "A".repeat(100);
            entity.setName(longName);
            assertEquals(100, entity.getName().length());
            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("description 可为 null 或空字符串")
        void descriptionShouldAcceptNullOrEmpty() {
            CollectionEntity entity = new CollectionEntity();

            entity.setDescription(null);
            assertNull(entity.getDescription());

            entity.setDescription("");
            assertEquals("", entity.getDescription());

            entity.setDescription("集合描述");
            assertEquals("集合描述", entity.getDescription());
        }

        @Test
        @DisplayName("sortOrder 应支持正数、零和负数")
        void sortOrderShouldSupportVariousValues() {
            CollectionEntity entity = new CollectionEntity();

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
            CollectionEntity entity = new CollectionEntity();

            entity.setDeleted(0);
            assertEquals(0, entity.getDeleted());

            entity.setDeleted(1);
            assertEquals(1, entity.getDeleted());
        }

        @Test
        @DisplayName("id 字段应支持 ASSIGN_ID 类型的 Long 值")
        void idShouldSupportLargeLongValues() {
            CollectionEntity entity = new CollectionEntity();
            entity.setId(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, entity.getId());

            entity.setId(1L);
            assertEquals(1L, entity.getId());
        }

        @Test
        @DisplayName("projectId 应为 null 可空")
        void projectIdShouldBeNullable() {
            CollectionEntity entity = new CollectionEntity();
            entity.setProjectId(null);
            assertNull(entity.getProjectId());

            entity.setProjectId(5001L);
            assertEquals(5001L, entity.getProjectId());
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

            CollectionEntity entity1 = buildFullEntity(1001L, now);
            CollectionEntity entity2 = buildFullEntity(1001L, now);

            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }

        @Test
        @DisplayName("不同 id 的实体应不相等")
        void entitiesWithDifferentIdsShouldNotBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            CollectionEntity entity1 = buildFullEntity(1001L, now);
            CollectionEntity entity2 = buildFullEntity(1002L, now);

            assertNotEquals(entity1, entity2);
        }

        @Test
        @DisplayName("空实体应与自身相等")
        void emptyEntityShouldBeEqualToItself() {
            CollectionEntity entity = new CollectionEntity();
            assertEquals(entity, entity);
            assertEquals(entity.hashCode(), entity.hashCode());
        }

        @Test
        @DisplayName("实体不应与 null 相等")
        void entityShouldNotEqualNull() {
            CollectionEntity entity = new CollectionEntity();
            assertNotEquals(null, entity);
        }

        @Test
        @DisplayName("实体不应与其他类型相等")
        void entityShouldNotEqualDifferentType() {
            CollectionEntity entity = new CollectionEntity();
            assertNotEquals("string", entity);
        }

        @Test
        @DisplayName("null id 的两个空实体应相等")
        void twoNullIdEntitiesShouldBeEqual() {
            CollectionEntity entity1 = new CollectionEntity();
            CollectionEntity entity2 = new CollectionEntity();
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
            CollectionEntity entity = new CollectionEntity();
            String str = entity.toString();
            assertTrue(str.contains("CollectionEntity"));
        }

        @Test
        @DisplayName("toString 应包含非空字段值")
        void toStringShouldIncludeNonNullFieldValues() {
            CollectionEntity entity = new CollectionEntity();
            entity.setId(1001L);
            entity.setName("用户管理接口集合");

            String str = entity.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("用户管理接口集合"));
        }

        @Test
        @DisplayName("toString 应处理 null 字段不抛异常")
        void toStringShouldHandleNullFields() {
            CollectionEntity entity = new CollectionEntity();
            String str = entity.toString();
            assertNotNull(str);
            assertTrue(str.contains("CollectionEntity"));
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
            CollectionEntity original = buildFullEntity(1001L, now);

            String json = objectMapper.writeValueAsString(original);
            CollectionEntity deserialized = objectMapper.readValue(json, CollectionEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("空实体序列化与反序列化应保持一致性")
        void emptyEntityShouldSerializeAndDeserialize() throws Exception {
            CollectionEntity original = new CollectionEntity();

            String json = objectMapper.writeValueAsString(original);
            CollectionEntity deserialized = objectMapper.readValue(json, CollectionEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("JSON 应包含所有非 null 字段")
        void jsonShouldContainNonNullFields() throws Exception {
            CollectionEntity entity = new CollectionEntity();
            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("用户管理接口集合");
            entity.setSortOrder(1);

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"id\":1001"));
            assertTrue(json.contains("\"projectId\":2001"));
            assertTrue(json.contains("\"name\":\"用户管理接口集合\""));
            assertTrue(json.contains("\"sortOrder\":1"));
        }

        @Test
        @DisplayName("JSON 不应包含 null 字段")
        void jsonShouldNotContainNullFields() throws Exception {
            CollectionEntity entity = new CollectionEntity();
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
        @DisplayName("实体应标注 @TableName(\"api_collection\") 且 autoResultMap = true")
        void shouldHaveTableNameAnnotation() {
            TableName annotation = CollectionEntity.class.getAnnotation(TableName.class);
            assertNotNull(annotation);
            assertEquals("api_collection", annotation.value());
            assertTrue(annotation.autoResultMap());
        }

        @Test
        @DisplayName("id 字段应标注 @TableId 且类型为 ASSIGN_ID")
        void idFieldShouldHaveTableIdAnnotation() throws Exception {
            java.lang.reflect.Field idField = CollectionEntity.class.getDeclaredField("id");
            TableId annotation = idField.getAnnotation(TableId.class);
            assertNotNull(annotation);
            assertEquals(IdType.ASSIGN_ID, annotation.type());
        }

        @Test
        @DisplayName("deleted 字段应标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws Exception {
            java.lang.reflect.Field deletedField = CollectionEntity.class.getDeclaredField("deleted");
            TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("createBy 字段应标注 @TableField(fill = INSERT)")
        void createByFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createByField = CollectionEntity.class.getDeclaredField("createBy");
            TableField annotation = createByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateBy 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateByFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateByField = CollectionEntity.class.getDeclaredField("updateBy");
            TableField annotation = updateByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }

        @Test
        @DisplayName("createTime 字段应标注 @TableField(fill = INSERT)")
        void createTimeFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createTimeField = CollectionEntity.class.getDeclaredField("createTime");
            TableField annotation = createTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateTime 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateTimeFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateTimeField = CollectionEntity.class.getDeclaredField("updateTime");
            TableField annotation = updateTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private CollectionEntity buildFullEntity(Long id, LocalDateTime now) {
        CollectionEntity entity = new CollectionEntity();
        entity.setId(id);
        entity.setProjectId(2001L);
        entity.setName("用户管理接口集合");
        entity.setDescription("包含用户管理模块的所有接口");
        entity.setSortOrder(1);
        entity.setDeleted(0);
        entity.setCreateBy(1L);
        entity.setUpdateBy(1L);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}