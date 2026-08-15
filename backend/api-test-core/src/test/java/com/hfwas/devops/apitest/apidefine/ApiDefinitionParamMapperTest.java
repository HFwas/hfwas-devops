package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionParamEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionParamMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiDefinitionParamMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及接口参数特有的业务语义（参数类型、必填、排序、嵌套结构）。
 *
 * @author hfwas
 */
@DisplayName("ApiDefinitionParamMapper — 接口参数数据访问测试")
class ApiDefinitionParamMapperTest extends BaseApiTest {

    @Autowired
    private ApiDefinitionParamMapper apiDefinitionParamMapper;

    private ApiDefinitionParamEntity userIdParam;
    private ApiDefinitionParamEntity userNameParam;
    private ApiDefinitionParamEntity pageSizeParam;

    @BeforeEach
    void setUp() {
        userIdParam = new ApiDefinitionParamEntity();
        userIdParam.setDefinitionId(1001L);
        userIdParam.setParamType("query");
        userIdParam.setName("userId");
        userIdParam.setDataType("integer");
        userIdParam.setRequired(1);
        userIdParam.setDefaultValue("1");
        userIdParam.setDescription("用户ID");
        userIdParam.setParentId(null);
        userIdParam.setSortOrder(1);
        userIdParam.setExample("12345");
        userIdParam.setDeleted(0);

        userNameParam = new ApiDefinitionParamEntity();
        userNameParam.setDefinitionId(1001L);
        userNameParam.setParamType("query");
        userNameParam.setName("userName");
        userNameParam.setDataType("string");
        userNameParam.setRequired(0);
        userNameParam.setDefaultValue(null);
        userNameParam.setDescription("用户名");
        userNameParam.setParentId(null);
        userNameParam.setSortOrder(2);
        userNameParam.setExample("张三");
        userNameParam.setDeleted(0);

        pageSizeParam = new ApiDefinitionParamEntity();
        pageSizeParam.setDefinitionId(2001L);
        pageSizeParam.setParamType("query");
        pageSizeParam.setName("pageSize");
        pageSizeParam.setDataType("integer");
        pageSizeParam.setRequired(0);
        pageSizeParam.setDefaultValue("20");
        pageSizeParam.setDescription("每页条数");
        pageSizeParam.setParentId(null);
        pageSizeParam.setSortOrder(1);
        pageSizeParam.setExample("20");
        pageSizeParam.setDeleted(0);
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入接口参数应成功并返回自增 ID")
        void insertShouldSucceed() {
            int affected = apiDefinitionParamMapper.insert(userIdParam);
            assertEquals(1, affected);
            assertNotNull(userIdParam.getId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            int affected1 = apiDefinitionParamMapper.insert(userIdParam);
            int affected2 = apiDefinitionParamMapper.insert(userNameParam);
            int affected3 = apiDefinitionParamMapper.insert(pageSizeParam);

            assertEquals(1, affected1);
            assertEquals(1, affected2);
            assertEquals(1, affected3);
            assertNotNull(userIdParam.getId());
            assertNotNull(userNameParam.getId());
            assertNotNull(pageSizeParam.getId());
        }

        @Test
        @DisplayName("插入时应自动填充 createBy、createTime、updateBy、updateTime")
        void insertShouldAutoFillAuditFields() {
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            userIdParam.setDeleted(null);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNotNull(saved);
            assertEquals(0, saved.getDeleted());
        }

        @Test
        @DisplayName("插入各参数类型的参数应成功")
        void insertAllParamTypesShouldSucceed() {
            userIdParam.setParamType("path");
            int affected = apiDefinitionParamMapper.insert(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("path", saved.getParamType());
        }

        @Test
        @DisplayName("插入必填参数应成功")
        void insertRequiredParamShouldSucceed() {
            userIdParam.setRequired(1);
            int affected = apiDefinitionParamMapper.insert(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(1, saved.getRequired());
        }

        @Test
        @DisplayName("插入可选参数应成功")
        void insertOptionalParamShouldSucceed() {
            userIdParam.setRequired(0);
            int affected = apiDefinitionParamMapper.insert(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(0, saved.getRequired());
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
            apiDefinitionParamMapper.insert(userIdParam);
            apiDefinitionParamMapper.insert(userNameParam);
            apiDefinitionParamMapper.insert(pageSizeParam);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确的接口参数")
        void selectByIdShouldReturnCorrectParam() {
            ApiDefinitionParamEntity found = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNotNull(found);
            assertEquals(userIdParam.getId(), found.getId());
            assertEquals("userId", found.getName());
            assertEquals("query", found.getParamType());
            assertEquals(1001L, found.getDefinitionId());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            ApiDefinitionParamEntity found = apiDefinitionParamMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有接口参数应返回全部记录（未逻辑删除的）")
        void selectListAllShouldReturnAllParams() {
            List<ApiDefinitionParamEntity> all = apiDefinitionParamMapper.selectList(null);
            assertNotNull(all);
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 definitionId 条件查询应返回正确结果")
        void selectByDefinitionIdShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(p -> p.getDefinitionId().equals(1001L)));
        }

        @Test
        @DisplayName("根据 paramType 条件查询应返回正确结果")
        void selectByParamTypeShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getParamType, "query")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(p -> "query".equals(p.getParamType())));
        }

        @Test
        @DisplayName("根据 name 条件查询应返回正确结果")
        void selectByNameShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getName, "userId")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("userId", result.get(0).getName());
        }

        @Test
        @DisplayName("根据 name 模糊查询应返回匹配的参数")
        void selectByNameLikeShouldReturnMatchingParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .like(ApiDefinitionParamEntity::getName, "user")
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(p -> p.getName().contains("user")));
        }

        @Test
        @DisplayName("根据 required 条件查询应返回正确结果")
        void selectByRequiredShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getRequired, 1)
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getRequired());
        }

        @Test
        @DisplayName("根据 dataType 条件查询应返回正确结果")
        void selectByDataTypeShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDataType, "integer")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 2);
            assertTrue(result.stream().allMatch(p -> "integer".equals(p.getDataType())));
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = apiDefinitionParamMapper.selectCount(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertNotNull(count);
            assertEquals(2, count);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(userIdParam.getId(), userNameParam.getId());
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("复合条件查询（definitionId + paramType）应返回正确结果")
        void selectByDefinitionIdAndParamTypeShouldReturnCorrectParams() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .eq(ApiDefinitionParamEntity::getParamType, "query")
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(p -> p.getDefinitionId().equals(1001L)));
            assertTrue(result.stream().allMatch(p -> "query".equals(p.getParamType())));
        }

        @Test
        @DisplayName("根据 sortOrder 排序查询应返回有序结果")
        void selectOrderBySortOrderShouldReturnOrderedResults() {
            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .orderByAsc(ApiDefinitionParamEntity::getSortOrder)
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.get(0).getSortOrder() <= result.get(1).getSortOrder());
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
            apiDefinitionParamMapper.insert(userIdParam);
            apiDefinitionParamMapper.insert(userNameParam);
        }

        @Test
        @DisplayName("更新参数名称应成功")
        void updateNameShouldSucceed() {
            userIdParam.setName("updatedUserId");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("updatedUserId", updated.getName());
        }

        @Test
        @DisplayName("更新参数类型应成功")
        void updateParamTypeShouldSucceed() {
            userIdParam.setParamType("header");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("header", updated.getParamType());
        }

        @Test
        @DisplayName("更新数据类型应成功")
        void updateDataTypeShouldSucceed() {
            userIdParam.setDataType("string");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("string", updated.getDataType());
        }

        @Test
        @DisplayName("更新必填状态应成功")
        void updateRequiredShouldSucceed() {
            userIdParam.setRequired(0);
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(0, updated.getRequired());
        }

        @Test
        @DisplayName("更新默认值应成功")
        void updateDefaultValueShouldSucceed() {
            userIdParam.setDefaultValue("100");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("100", updated.getDefaultValue());
        }

        @Test
        @DisplayName("更新参数描述应成功")
        void updateDescriptionShouldSucceed() {
            userIdParam.setDescription("更新后的描述");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("更新后的描述", updated.getDescription());
        }

        @Test
        @DisplayName("更新排序序号应成功")
        void updateSortOrderShouldSucceed() {
            userIdParam.setSortOrder(99);
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(99, updated.getSortOrder());
        }

        @Test
        @DisplayName("更新示例值应成功")
        void updateExampleShouldSucceed() {
            userIdParam.setExample("99999");
            int affected = apiDefinitionParamMapper.updateById(userIdParam);
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("99999", updated.getExample());
        }

        @Test
        @DisplayName("更新时 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillUpdateFields() {
            userIdParam.setName("更新名称");
            apiDefinitionParamMapper.updateById(userIdParam);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(testUserId(), updated.getUpdateBy());
            assertNotNull(updated.getUpdateTime());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            ApiDefinitionParamEntity nonExistent = new ApiDefinitionParamEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = apiDefinitionParamMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            int affected = apiDefinitionParamMapper.update(
                    null,
                    Wrappers.<ApiDefinitionParamEntity>lambdaUpdate()
                            .set(ApiDefinitionParamEntity::getRequired, 1)
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .eq(ApiDefinitionParamEntity::getName, "userName")
            );
            assertEquals(1, affected);

            ApiDefinitionParamEntity updated = apiDefinitionParamMapper.selectById(userNameParam.getId());
            assertEquals(1, updated.getRequired());

            ApiDefinitionParamEntity unchanged = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(1, unchanged.getRequired());
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
            apiDefinitionParamMapper.insert(userIdParam);
            apiDefinitionParamMapper.insert(userNameParam);
            apiDefinitionParamMapper.insert(pageSizeParam);
        }

        @Test
        @DisplayName("逻辑删除接口参数应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = apiDefinitionParamMapper.deleteById(userIdParam.getId());
            assertEquals(1, affected);

            List<ApiDefinitionParamEntity> all = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getId, userIdParam.getId())
            );
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            apiDefinitionParamMapper.deleteById(userIdParam.getId());

            ApiDefinitionParamEntity found = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = apiDefinitionParamMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(userIdParam.getId(), userNameParam.getId());
            int affected = apiDefinitionParamMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            List<ApiDefinitionParamEntity> remaining = apiDefinitionParamMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            int affected = apiDefinitionParamMapper.delete(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getName, "pageSize")
            );
            assertEquals(1, affected);

            assertNull(apiDefinitionParamMapper.selectById(pageSizeParam.getId()));
            assertNotNull(apiDefinitionParamMapper.selectById(userIdParam.getId()));
            assertNotNull(apiDefinitionParamMapper.selectById(userNameParam.getId()));
        }
    }

    // ========================================================================
    // 业务语义 — 参数类型
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 参数类型")
    class ParamTypeSemantics {

        private ApiDefinitionParamEntity pathParam;
        private ApiDefinitionParamEntity headerParam;
        private ApiDefinitionParamEntity bodyParam;

        @BeforeEach
        void insertData() {
            apiDefinitionParamMapper.insert(userIdParam);
            apiDefinitionParamMapper.insert(userNameParam);

            pathParam = new ApiDefinitionParamEntity();
            pathParam.setDefinitionId(1001L);
            pathParam.setParamType("path");
            pathParam.setName("id");
            pathParam.setDataType("integer");
            pathParam.setRequired(1);
            pathParam.setDescription("路径参数：用户ID");
            pathParam.setSortOrder(1);
            pathParam.setDeleted(0);
            apiDefinitionParamMapper.insert(pathParam);

            headerParam = new ApiDefinitionParamEntity();
            headerParam.setDefinitionId(1001L);
            headerParam.setParamType("header");
            headerParam.setName("Authorization");
            headerParam.setDataType("string");
            headerParam.setRequired(1);
            headerParam.setDescription("认证令牌");
            headerParam.setSortOrder(1);
            headerParam.setDeleted(0);
            apiDefinitionParamMapper.insert(headerParam);

            bodyParam = new ApiDefinitionParamEntity();
            bodyParam.setDefinitionId(1001L);
            bodyParam.setParamType("body");
            bodyParam.setName("requestBody");
            bodyParam.setDataType("object");
            bodyParam.setRequired(1);
            bodyParam.setDescription("请求体");
            bodyParam.setSortOrder(1);
            bodyParam.setDeleted(0);
            apiDefinitionParamMapper.insert(bodyParam);
        }

        @Test
        @DisplayName("同一接口定义下可存在多种参数类型")
        void sameDefinitionCanHaveDifferentParamTypes() {
            List<ApiDefinitionParamEntity> params = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertTrue(params.size() >= 5);
        }

        @Test
        @DisplayName("按 paramType 筛选 path 参数应返回正确结果")
        void selectByParamTypePathShouldReturnCorrect() {
            List<ApiDefinitionParamEntity> pathParams = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .eq(ApiDefinitionParamEntity::getParamType, "path")
            );
            assertEquals(1, pathParams.size());
            assertEquals("id", pathParams.get(0).getName());
        }

        @Test
        @DisplayName("按 paramType 筛选 header 参数应返回正确结果")
        void selectByParamTypeHeaderShouldReturnCorrect() {
            List<ApiDefinitionParamEntity> headerParams = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .eq(ApiDefinitionParamEntity::getParamType, "header")
            );
            assertEquals(1, headerParams.size());
            assertEquals("Authorization", headerParams.get(0).getName());
        }

        @Test
        @DisplayName("按 paramType 筛选 body 参数应返回正确结果")
        void selectByParamTypeBodyShouldReturnCorrect() {
            List<ApiDefinitionParamEntity> bodyParams = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .eq(ApiDefinitionParamEntity::getParamType, "body")
            );
            assertEquals(1, bodyParams.size());
            assertEquals("requestBody", bodyParams.get(0).getName());
        }
    }

    // ========================================================================
    // 业务语义 — 嵌套参数（parentId）
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 嵌套参数（parentId）")
    class NestedParamSemantics {

        private ApiDefinitionParamEntity bodyParam;
        private ApiDefinitionParamEntity nestedField1;
        private ApiDefinitionParamEntity nestedField2;

        @BeforeEach
        void insertNestedData() {
            bodyParam = new ApiDefinitionParamEntity();
            bodyParam.setDefinitionId(1001L);
            bodyParam.setParamType("body");
            bodyParam.setName("user");
            bodyParam.setDataType("object");
            bodyParam.setRequired(1);
            bodyParam.setDescription("用户对象");
            bodyParam.setSortOrder(1);
            bodyParam.setDeleted(0);
            apiDefinitionParamMapper.insert(bodyParam);

            nestedField1 = new ApiDefinitionParamEntity();
            nestedField1.setDefinitionId(1001L);
            nestedField1.setParamType("body");
            nestedField1.setName("name");
            nestedField1.setDataType("string");
            nestedField1.setRequired(1);
            nestedField1.setParentId(bodyParam.getId());
            nestedField1.setDescription("用户名");
            nestedField1.setSortOrder(1);
            nestedField1.setDeleted(0);
            apiDefinitionParamMapper.insert(nestedField1);

            nestedField2 = new ApiDefinitionParamEntity();
            nestedField2.setDefinitionId(1001L);
            nestedField2.setParamType("body");
            nestedField2.setName("age");
            nestedField2.setDataType("integer");
            nestedField2.setRequired(0);
            nestedField2.setParentId(bodyParam.getId());
            nestedField2.setDescription("年龄");
            nestedField2.setSortOrder(2);
            nestedField2.setDeleted(0);
            apiDefinitionParamMapper.insert(nestedField2);
        }

        @Test
        @DisplayName("父参数应正确关联子参数")
        void parentParamShouldHaveCorrectChildren() {
            List<ApiDefinitionParamEntity> children = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getParentId, bodyParam.getId())
            );
            assertEquals(2, children.size());
            assertTrue(children.stream().allMatch(c -> c.getParentId().equals(bodyParam.getId())));
        }

        @Test
        @DisplayName("子参数应正确关联父参数")
        void childParamShouldHaveCorrectParent() {
            ApiDefinitionParamEntity fetched = apiDefinitionParamMapper.selectById(nestedField1.getId());
            assertNotNull(fetched);
            assertEquals(bodyParam.getId(), fetched.getParentId());
        }

        @Test
        @DisplayName("多层嵌套应正确关联")
        void multiLevelNestingShouldHaveCorrectReferences() {
            ApiDefinitionParamEntity subChild = new ApiDefinitionParamEntity();
            subChild.setDefinitionId(1001L);
            subChild.setParamType("body");
            subChild.setName("firstName");
            subChild.setDataType("string");
            subChild.setRequired(1);
            subChild.setParentId(nestedField1.getId());
            subChild.setDescription("名字");
            subChild.setSortOrder(1);
            subChild.setDeleted(0);
            apiDefinitionParamMapper.insert(subChild);

            ApiDefinitionParamEntity fetched = apiDefinitionParamMapper.selectById(subChild.getId());
            assertEquals(nestedField1.getId(), fetched.getParentId());
        }

        @Test
        @DisplayName("查询 parentId 为 null 的根级参数应返回正确结果")
        void selectNullParentIdShouldReturnRootParams() {
            List<ApiDefinitionParamEntity> rootParams = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .isNull(ApiDefinitionParamEntity::getParentId)
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertEquals(1, rootParams.size());
            assertEquals("user", rootParams.get(0).getName());
        }
    }

    // ========================================================================
    // 业务语义 — 排序
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 排序")
    class ParamSorting {

        @Test
        @DisplayName("同一接口定义下按 sortOrder 升序查询应返回有序结果")
        void selectBySortOrderAscShouldReturnOrdered() {
            for (int i = 1; i <= 5; i++) {
                ApiDefinitionParamEntity param = new ApiDefinitionParamEntity();
                param.setDefinitionId(1001L);
                param.setParamType("query");
                param.setName("param" + i);
                param.setDataType("string");
                param.setSortOrder(i);
                param.setDeleted(0);
                apiDefinitionParamMapper.insert(param);
            }

            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .orderByAsc(ApiDefinitionParamEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("同一接口定义下按 sortOrder 降序查询应返回有序结果")
        void selectBySortOrderDescShouldReturnOrdered() {
            for (int i = 1; i <= 5; i++) {
                ApiDefinitionParamEntity param = new ApiDefinitionParamEntity();
                param.setDefinitionId(1001L);
                param.setParamType("query");
                param.setName("param" + i);
                param.setDataType("string");
                param.setSortOrder(i);
                param.setDeleted(0);
                apiDefinitionParamMapper.insert(param);
            }

            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
                            .orderByDesc(ApiDefinitionParamEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() >= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("相同 sortOrder 的参数应能共存")
        void paramsWithSameSortOrderShouldCoexist() {
            ApiDefinitionParamEntity p1 = new ApiDefinitionParamEntity();
            p1.setDefinitionId(1001L);
            p1.setParamType("query");
            p1.setName("paramA");
            p1.setDataType("string");
            p1.setSortOrder(1);
            p1.setDeleted(0);
            apiDefinitionParamMapper.insert(p1);

            ApiDefinitionParamEntity p2 = new ApiDefinitionParamEntity();
            p2.setDefinitionId(1001L);
            p2.setParamType("query");
            p2.setName("paramB");
            p2.setDataType("string");
            p2.setSortOrder(1);
            p2.setDeleted(0);
            apiDefinitionParamMapper.insert(p2);

            List<ApiDefinitionParamEntity> result = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getSortOrder, 1)
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertEquals(2, result.size());
        }
    }

    // ========================================================================
    // 边界条件
    // ========================================================================

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

        @Test
        @DisplayName("参数名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "param" + "A".repeat(100);
            userIdParam.setName(longName);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("参数描述为超长字符串时插入与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            userIdParam.setDescription(longDesc);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(longDesc, saved.getDescription());
        }

        @Test
        @DisplayName("description 为 null 时插入与查询应正确")
        void nullDescriptionShouldBeStoredCorrectly() {
            userIdParam.setDescription(null);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNull(saved.getDescription());
        }

        @Test
        @DisplayName("description 为空字符串时插入与查询应正确")
        void emptyDescriptionShouldBeStoredCorrectly() {
            userIdParam.setDescription("");
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("", saved.getDescription());
        }

        @Test
        @DisplayName("defaultValue 为 null 时插入与查询应正确")
        void nullDefaultValueShouldBeStoredCorrectly() {
            userIdParam.setDefaultValue(null);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNull(saved.getDefaultValue());
        }

        @Test
        @DisplayName("defaultValue 为空字符串时插入与查询应正确")
        void emptyDefaultValueShouldBeStoredCorrectly() {
            userIdParam.setDefaultValue("");
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("", saved.getDefaultValue());
        }

        @Test
        @DisplayName("example 为 null 时插入与查询应正确")
        void nullExampleShouldBeStoredCorrectly() {
            userIdParam.setExample(null);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNull(saved.getExample());
        }

        @Test
        @DisplayName("example 为空字符串时插入与查询应正确")
        void emptyExampleShouldBeStoredCorrectly() {
            userIdParam.setExample("");
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals("", saved.getExample());
        }

        @Test
        @DisplayName("sortOrder 为最小值时插入与查询应正确")
        void minSortOrderShouldBeStoredCorrectly() {
            userIdParam.setSortOrder(Integer.MIN_VALUE);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(Integer.MIN_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时插入与查询应正确")
        void maxSortOrderShouldBeStoredCorrectly() {
            userIdParam.setSortOrder(Integer.MAX_VALUE);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(Integer.MAX_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("definitionId 为 0 时插入与查询应正确")
        void zeroDefinitionIdShouldBeStoredCorrectly() {
            userIdParam.setDefinitionId(0L);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(0L, saved.getDefinitionId());
        }

        @Test
        @DisplayName("definitionId 为负数时插入与查询应正确")
        void negativeDefinitionIdShouldBeStoredCorrectly() {
            userIdParam.setDefinitionId(-1L);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertEquals(-1L, saved.getDefinitionId());
        }

        @Test
        @DisplayName("parentId 为 null 时插入与查询应正确")
        void nullParentIdShouldBeStoredCorrectly() {
            userIdParam.setParentId(null);
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(userIdParam.getId());
            assertNull(saved.getParentId());
        }

        @Test
        @DisplayName("dataType 支持所有标准类型")
        void allDataTypesShouldBeStoredCorrectly() {
            String[] dataTypes = {"string", "integer", "number", "boolean", "array", "object", "file"};
            for (String dataType : dataTypes) {
                ApiDefinitionParamEntity param = new ApiDefinitionParamEntity();
                param.setDefinitionId(1001L);
                param.setParamType("query");
                param.setName("param_" + dataType);
                param.setDataType(dataType);
                param.setDeleted(0);
                apiDefinitionParamMapper.insert(param);

                ApiDefinitionParamEntity saved = apiDefinitionParamMapper.selectById(param.getId());
                assertEquals(dataType, saved.getDataType());
            }
        }

        @Test
        @DisplayName("不同接口定义下的参数应互不干扰")
        void paramsInDifferentDefinitionsShouldBeIndependent() {
            apiDefinitionParamMapper.insert(userIdParam);

            ApiDefinitionParamEntity otherDefParam = new ApiDefinitionParamEntity();
            otherDefParam.setDefinitionId(9999L);
            otherDefParam.setParamType("query");
            otherDefParam.setName("otherParam");
            otherDefParam.setDataType("string");
            otherDefParam.setDeleted(0);
            apiDefinitionParamMapper.insert(otherDefParam);

            List<ApiDefinitionParamEntity> definitionParams = apiDefinitionParamMapper.selectList(
                    Wrappers.<ApiDefinitionParamEntity>lambdaQuery()
                            .eq(ApiDefinitionParamEntity::getDefinitionId, 1001L)
            );
            assertTrue(definitionParams.stream().allMatch(p -> p.getDefinitionId().equals(1001L)));
        }
    }
}