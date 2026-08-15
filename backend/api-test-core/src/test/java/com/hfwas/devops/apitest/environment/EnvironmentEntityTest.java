package com.hfwas.devops.apitest.environment;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hfwas.devops.apitest.environment.entity.EnvironmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnvironmentEntity 实体类测试
 * <p>
 * 验证 Lombok 注解、MyBatis-Plus 注解、字段赋值、JSON 序列化/反序列化
 * 以及 equals/hashCode/toString 等行为。
 *
 * @author hfwas
 */
@DisplayName("EnvironmentEntity — 环境实体测试")
class EnvironmentEntityTest {

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
            EnvironmentEntity entity = new EnvironmentEntity();
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
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("测试环境");
            entity.setDescription("用于接口测试的测试环境");
            entity.setSortOrder(1);
            entity.setDeleted(0);
            entity.setCreateBy(1L);
            entity.setUpdateBy(1L);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            assertEquals(1001L, entity.getId());
            assertEquals(2001L, entity.getProjectId());
            assertEquals("测试环境", entity.getName());
            assertEquals("用于接口测试的测试环境", entity.getDescription());
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
            EnvironmentEntity entity = new EnvironmentEntity();
            String longName = "A".repeat(100);
            entity.setName(longName);
            assertEquals(100, entity.getName().length());
            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("name 可为 null 或空字符串")
        void nameShouldAcceptNullOrEmpty() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setName(null);
            assertNull(entity.getName());

            entity.setName("");
            assertEquals("", entity.getName());

            entity.setName("生产环境");
            assertEquals("生产环境", entity.getName());
        }

        @Test
        @DisplayName("description 可为 null 或空字符串")
        void descriptionShouldAcceptNullOrEmpty() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setDescription(null);
            assertNull(entity.getDescription());

            entity.setDescription("");
            assertEquals("", entity.getDescription());

            entity.setDescription("环境描述文本");
            assertEquals("环境描述文本", entity.getDescription());
        }

        @Test
        @DisplayName("sortOrder 应支持正序和倒序排序值")
        void sortOrderShouldSupportPositiveAndNegativeValues() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setSortOrder(0);
            assertEquals(0, entity.getSortOrder());

            entity.setSortOrder(1);
            assertEquals(1, entity.getSortOrder());

            entity.setSortOrder(99);
            assertEquals(99, entity.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 可为 null")
        void sortOrderShouldBeNullable() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setSortOrder(null);
            assertNull(entity.getSortOrder());

            entity.setSortOrder(5);
            assertEquals(5, entity.getSortOrder());
        }

        @Test
        @DisplayName("deleted 应支持 0 和 1 两个值")
        void deletedShouldSupportZeroAndOne() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setDeleted(0);
            assertEquals(0, entity.getDeleted());

            entity.setDeleted(1);
            assertEquals(1, entity.getDeleted());
        }

        @Test
        @DisplayName("id 字段应支持 ASSIGN_ID 类型的 Long 值")
        void idShouldSupportLargeLongValues() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setId(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, entity.getId());

            entity.setId(1L);
            assertEquals(1L, entity.getId());
        }

        @Test
        @DisplayName("projectId 应支持空值和大值")
        void projectIdShouldSupportNullAndLargeValues() {
            EnvironmentEntity entity = new EnvironmentEntity();

            entity.setProjectId(null);
            assertNull(entity.getProjectId());

            entity.setProjectId(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, entity.getProjectId());

            entity.setProjectId(1001L);
            assertEquals(1001L, entity.getProjectId());
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

            EnvironmentEntity entity1 = buildFullEntity(1001L, now);
            EnvironmentEntity entity2 = buildFullEntity(1001L, now);

            assertEquals(entity1, entity2);
            assertEquals(entity1.hashCode(), entity2.hashCode());
        }

        @Test
        @DisplayName("不同 id 的实体应不相等")
        void entitiesWithDifferentIdsShouldNotBeEqual() {
            LocalDateTime now = LocalDateTime.now();

            EnvironmentEntity entity1 = buildFullEntity(1001L, now);
            EnvironmentEntity entity2 = buildFullEntity(1002L, now);

            assertNotEquals(entity1, entity2);
        }

        @Test
        @DisplayName("空实体应与自身相等")
        void emptyEntityShouldBeEqualToItself() {
            EnvironmentEntity entity = new EnvironmentEntity();
            assertEquals(entity, entity);
            assertEquals(entity.hashCode(), entity.hashCode());
        }

        @Test
        @DisplayName("实体不应与 null 相等")
        void entityShouldNotEqualNull() {
            EnvironmentEntity entity = new EnvironmentEntity();
            assertNotEquals(null, entity);
        }

        @Test
        @DisplayName("实体不应与其他类型相等")
        void entityShouldNotEqualDifferentType() {
            EnvironmentEntity entity = new EnvironmentEntity();
            assertNotEquals("string", entity);
        }

        @Test
        @DisplayName("null id 的两个空实体应相等")
        void twoNullIdEntitiesShouldBeEqual() {
            EnvironmentEntity entity1 = new EnvironmentEntity();
            EnvironmentEntity entity2 = new EnvironmentEntity();
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
            EnvironmentEntity entity = new EnvironmentEntity();
            String str = entity.toString();
            assertTrue(str.contains("EnvironmentEntity"));
        }

        @Test
        @DisplayName("toString 应包含非空字段值")
        void toStringShouldIncludeNonNullFieldValues() {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.setId(1001L);
            entity.setName("测试环境");
            entity.setSortOrder(1);

            String str = entity.toString();
            assertTrue(str.contains("1001"));
            assertTrue(str.contains("测试环境"));
            assertTrue(str.contains("1"));
        }

        @Test
        @DisplayName("toString 应处理 null 字段不抛异常")
        void toStringShouldHandleNullFields() {
            EnvironmentEntity entity = new EnvironmentEntity();
            String str = entity.toString();
            assertNotNull(str);
            assertTrue(str.contains("EnvironmentEntity"));
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
            EnvironmentEntity original = buildFullEntity(1001L, now);

            String json = objectMapper.writeValueAsString(original);
            EnvironmentEntity deserialized = objectMapper.readValue(json, EnvironmentEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("空实体序列化与反序列化应保持一致性")
        void emptyEntityShouldSerializeAndDeserialize() throws Exception {
            EnvironmentEntity original = new EnvironmentEntity();

            String json = objectMapper.writeValueAsString(original);
            EnvironmentEntity deserialized = objectMapper.readValue(json, EnvironmentEntity.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("JSON 应包含所有非 null 字段")
        void jsonShouldContainNonNullFields() throws Exception {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.setId(1001L);
            entity.setProjectId(2001L);
            entity.setName("测试环境");
            entity.setDescription("测试环境描述");
            entity.setSortOrder(1);

            String json = objectMapper.writeValueAsString(entity);
            assertTrue(json.contains("\"id\":1001"));
            assertTrue(json.contains("\"projectId\":2001"));
            assertTrue(json.contains("\"name\":\"测试环境\""));
            assertTrue(json.contains("\"description\":\"测试环境描述\""));
            assertTrue(json.contains("\"sortOrder\":1"));
        }

        @Test
        @DisplayName("JSON 不应包含 null 字段")
        void jsonShouldNotContainNullFields() throws Exception {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.setId(1001L);

            String json = objectMapper.writeValueAsString(entity);
            assertFalse(json.contains("\"createTime\""));
            assertFalse(json.contains("\"updateTime\""));
            assertFalse(json.contains("\"deleted\""));
            assertFalse(json.contains("\"description\""));
            assertFalse(json.contains("\"sortOrder\""));
        }

        @Test
        @DisplayName("LocalDateTime 字段应正确序列化与反序列化")
        void localDateTimeFieldsShouldSerializeAndDeserialize() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.setId(1001L);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            String json = objectMapper.writeValueAsString(entity);
            EnvironmentEntity deserialized = objectMapper.readValue(json, EnvironmentEntity.class);

            assertEquals(now, deserialized.getCreateTime());
            assertEquals(now, deserialized.getUpdateTime());
        }
    }

    // ========================================================================
    // MyBatis-Plus 注解验证
    // ========================================================================

    @Nested
    @DisplayName("MyBatis-Plus 注解验证")
    class MyBatisPlusAnnotations {

        @Test
        @DisplayName("实体应标注 @TableName(\"api_environment\") 且 autoResultMap = true")
        void shouldHaveTableNameAnnotation() {
            TableName annotation = EnvironmentEntity.class.getAnnotation(TableName.class);
            assertNotNull(annotation);
            assertEquals("api_environment", annotation.value());
            assertTrue(annotation.autoResultMap());
        }

        @Test
        @DisplayName("id 字段应标注 @TableId 且类型为 ASSIGN_ID")
        void idFieldShouldHaveTableIdAnnotation() throws Exception {
            java.lang.reflect.Field idField = EnvironmentEntity.class.getDeclaredField("id");
            TableId annotation = idField.getAnnotation(TableId.class);
            assertNotNull(annotation);
            assertEquals(IdType.ASSIGN_ID, annotation.type());
        }

        @Test
        @DisplayName("deleted 字段应标注 @TableLogic")
        void deletedFieldShouldHaveTableLogicAnnotation() throws Exception {
            java.lang.reflect.Field deletedField = EnvironmentEntity.class.getDeclaredField("deleted");
            TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(annotation);
        }

        @Test
        @DisplayName("createBy 字段应标注 @TableField(fill = INSERT)")
        void createByFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createByField = EnvironmentEntity.class.getDeclaredField("createBy");
            TableField annotation = createByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateBy 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateByFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateByField = EnvironmentEntity.class.getDeclaredField("updateBy");
            TableField annotation = updateByField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }

        @Test
        @DisplayName("createTime 字段应标注 @TableField(fill = INSERT)")
        void createTimeFieldShouldHaveInsertFill() throws Exception {
            java.lang.reflect.Field createTimeField = EnvironmentEntity.class.getDeclaredField("createTime");
            TableField annotation = createTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT, annotation.fill());
        }

        @Test
        @DisplayName("updateTime 字段应标注 @TableField(fill = INSERT_UPDATE)")
        void updateTimeFieldShouldHaveInsertUpdateFill() throws Exception {
            java.lang.reflect.Field updateTimeField = EnvironmentEntity.class.getDeclaredField("updateTime");
            TableField annotation = updateTimeField.getAnnotation(TableField.class);
            assertNotNull(annotation);
            assertEquals(FieldFill.INSERT_UPDATE, annotation.fill());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private EnvironmentEntity buildFullEntity(Long id, LocalDateTime now) {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setId(id);
        entity.setProjectId(2001L);
        entity.setName("测试环境");
        entity.setDescription("用于接口测试的测试环境");
        entity.setSortOrder(1);
        entity.setDeleted(0);
        entity.setCreateBy(1L);
        entity.setUpdateBy(1L);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}