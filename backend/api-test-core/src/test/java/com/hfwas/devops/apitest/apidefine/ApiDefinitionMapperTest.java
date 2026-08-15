package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
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
 * ApiDefinitionMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及接口定义特有的业务语义（分组、方法、状态、标签、版本）。
 *
 * @author hfwas
 */
@DisplayName("ApiDefinitionMapper — 接口定义数据访问测试")
class ApiDefinitionMapperTest extends BaseApiTest {

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    private ApiDefinitionEntity getUsersApi;
    private ApiDefinitionEntity createUserApi;
    private ApiDefinitionEntity deleteUserApi;

    @BeforeEach
    void setUp() {
        getUsersApi = new ApiDefinitionEntity();
        getUsersApi.setProjectId(testProjectId());
        getUsersApi.setGroupId(1001L);
        getUsersApi.setName("获取用户列表");
        getUsersApi.setPath("/api/users");
        getUsersApi.setMethod("GET");
        getUsersApi.setStatus("DRAFT");
        getUsersApi.setVersion("1.0.0");
        getUsersApi.setTags(Arrays.asList("用户", "查询"));
        getUsersApi.setDescription("分页获取用户列表");
        getUsersApi.setProtocol("HTTP");
        getUsersApi.setHost("localhost:8080");
        getUsersApi.setContentType("application/json");
        getUsersApi.setDeleted(0);

        createUserApi = new ApiDefinitionEntity();
        createUserApi.setProjectId(testProjectId());
        createUserApi.setGroupId(1001L);
        createUserApi.setName("创建用户");
        createUserApi.setPath("/api/users");
        createUserApi.setMethod("POST");
        createUserApi.setStatus("PUBLISHED");
        createUserApi.setVersion("1.0.0");
        createUserApi.setTags(Arrays.asList("用户", "创建"));
        createUserApi.setDescription("创建新用户");
        createUserApi.setProtocol("HTTP");
        createUserApi.setHost("localhost:8080");
        createUserApi.setContentType("application/json");
        createUserApi.setDeleted(0);

        deleteUserApi = new ApiDefinitionEntity();
        deleteUserApi.setProjectId(testProjectId());
        deleteUserApi.setGroupId(2001L);
        deleteUserApi.setName("删除用户");
        deleteUserApi.setPath("/api/users/{id}");
        deleteUserApi.setMethod("DELETE");
        deleteUserApi.setStatus("PUBLISHED");
        deleteUserApi.setVersion("2.0.0");
        deleteUserApi.setTags(Arrays.asList("用户", "删除"));
        deleteUserApi.setDescription("根据 ID 删除用户");
        deleteUserApi.setProtocol("HTTPS");
        deleteUserApi.setHost("api.example.com");
        deleteUserApi.setContentType("application/json");
        deleteUserApi.setDeleted(0);
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入接口定义应成功并返回自增 ID")
        void insertShouldSucceed() {
            int affected = apiDefinitionMapper.insert(getUsersApi);
            assertEquals(1, affected);
            assertNotNull(getUsersApi.getId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            int affected1 = apiDefinitionMapper.insert(getUsersApi);
            int affected2 = apiDefinitionMapper.insert(createUserApi);
            int affected3 = apiDefinitionMapper.insert(deleteUserApi);

            assertEquals(1, affected1);
            assertEquals(1, affected2);
            assertEquals(1, affected3);
            assertNotNull(getUsersApi.getId());
            assertNotNull(createUserApi.getId());
            assertNotNull(deleteUserApi.getId());
        }

        @Test
        @DisplayName("插入时应自动填充 createBy、createTime、updateBy、updateTime")
        void insertShouldAutoFillAuditFields() {
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            getUsersApi.setDeleted(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(saved);
            assertEquals(0, saved.getDeleted());
        }

        @Test
        @DisplayName("插入含 tags 列表的接口应正确存储")
        void insertWithTagsShouldSucceed() {
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(saved.getTags());
            assertEquals(2, saved.getTags().size());
            assertTrue(saved.getTags().contains("用户"));
            assertTrue(saved.getTags().contains("查询"));
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
            apiDefinitionMapper.insert(getUsersApi);
            apiDefinitionMapper.insert(createUserApi);
            apiDefinitionMapper.insert(deleteUserApi);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确的接口定义")
        void selectByIdShouldReturnCorrectApi() {
            ApiDefinitionEntity found = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(found);
            assertEquals(getUsersApi.getId(), found.getId());
            assertEquals("获取用户列表", found.getName());
            assertEquals("GET", found.getMethod());
            assertEquals(testProjectId(), found.getProjectId());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            ApiDefinitionEntity found = apiDefinitionMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有接口定义应返回全部记录（未逻辑删除的）")
        void selectListAllShouldReturnAllApis() {
            List<ApiDefinitionEntity> all = apiDefinitionMapper.selectList(null);
            assertNotNull(all);
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 projectId 条件查询应返回正确结果")
        void selectByProjectIdShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getProjectId, testProjectId())
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(a -> a.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("根据 groupId 条件查询应返回正确结果")
        void selectByGroupIdShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getGroupId, 1001L)
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> a.getGroupId().equals(1001L)));
        }

        @Test
        @DisplayName("根据 method 条件查询应返回正确结果")
        void selectByMethodShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getMethod, "GET")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("GET", result.get(0).getMethod());
        }

        @Test
        @DisplayName("根据 status 条件查询应返回正确结果")
        void selectByStatusShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getStatus, "PUBLISHED")
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> "PUBLISHED".equals(a.getStatus())));
        }

        @Test
        @DisplayName("根据 name 模糊查询应返回匹配的接口")
        void selectByNameLikeShouldReturnMatchingApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .like(ApiDefinitionEntity::getName, "用户")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 2);
            assertTrue(result.stream().allMatch(a -> a.getName().contains("用户")));
        }

        @Test
        @DisplayName("根据 path 精确查询应返回正确结果")
        void selectByPathShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getPath, "/api/users")
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> "/api/users".equals(a.getPath())));
        }

        @Test
        @DisplayName("根据 version 条件查询应返回正确结果")
        void selectByVersionShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getVersion, "1.0.0")
            );
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(a -> "1.0.0".equals(a.getVersion())));
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = apiDefinitionMapper.selectCount(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getProjectId, testProjectId())
            );
            assertNotNull(count);
            assertTrue(count >= 3);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(getUsersApi.getId(), createUserApi.getId());
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("复合条件查询（method + status）应返回正确结果")
        void selectByMethodAndStatusShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getMethod, "POST")
                            .eq(ApiDefinitionEntity::getStatus, "PUBLISHED")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("创建用户", result.get(0).getName());
        }

        @Test
        @DisplayName("按 protocol 条件查询应返回正确结果")
        void selectByProtocolShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getProtocol, "HTTPS")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("HTTPS", result.get(0).getProtocol());
        }

        @Test
        @DisplayName("按 contentType 条件查询应返回正确结果")
        void selectByContentTypeShouldReturnCorrectApis() {
            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getContentType, "application/json")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(a -> "application/json".equals(a.getContentType())));
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
            apiDefinitionMapper.insert(getUsersApi);
            apiDefinitionMapper.insert(createUserApi);
        }

        @Test
        @DisplayName("更新接口名称应成功")
        void updateNameShouldSucceed() {
            getUsersApi.setName("获取用户列表V2");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("获取用户列表V2", updated.getName());
        }

        @Test
        @DisplayName("更新接口路径应成功")
        void updatePathShouldSucceed() {
            getUsersApi.setPath("/api/users/v2");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("/api/users/v2", updated.getPath());
        }

        @Test
        @DisplayName("更新 HTTP 方法应成功")
        void updateMethodShouldSucceed() {
            getUsersApi.setMethod("POST");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("POST", updated.getMethod());
        }

        @Test
        @DisplayName("更新接口状态应成功")
        void updateStatusShouldSucceed() {
            getUsersApi.setStatus("PUBLISHED");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("PUBLISHED", updated.getStatus());
        }

        @Test
        @DisplayName("更新接口版本号应成功")
        void updateVersionShouldSucceed() {
            getUsersApi.setVersion("2.0.0");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("2.0.0", updated.getVersion());
        }

        @Test
        @DisplayName("更新接口分组应成功")
        void updateGroupIdShouldSucceed() {
            getUsersApi.setGroupId(9999L);
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(9999L, updated.getGroupId());
        }

        @Test
        @DisplayName("更新接口描述应成功")
        void updateDescriptionShouldSucceed() {
            getUsersApi.setDescription("更新后的描述");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("更新后的描述", updated.getDescription());
        }

        @Test
        @DisplayName("更新接口 tags 应成功")
        void updateTagsShouldSucceed() {
            getUsersApi.setTags(Arrays.asList("用户", "查询", "分页"));
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(updated.getTags());
            assertEquals(3, updated.getTags().size());
            assertTrue(updated.getTags().contains("分页"));
        }

        @Test
        @DisplayName("更新接口 host 应成功")
        void updateHostShouldSucceed() {
            getUsersApi.setHost("prod-api.example.com");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("prod-api.example.com", updated.getHost());
        }

        @Test
        @DisplayName("更新接口 protocol 应成功")
        void updateProtocolShouldSucceed() {
            getUsersApi.setProtocol("HTTPS");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("HTTPS", updated.getProtocol());
        }

        @Test
        @DisplayName("更新接口 contentType 应成功")
        void updateContentTypeShouldSucceed() {
            getUsersApi.setContentType("application/xml");
            int affected = apiDefinitionMapper.updateById(getUsersApi);
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("application/xml", updated.getContentType());
        }

        @Test
        @DisplayName("更新时 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillUpdateFields() {
            getUsersApi.setName("更新名称");
            apiDefinitionMapper.updateById(getUsersApi);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(testUserId(), updated.getUpdateBy());
            assertNotNull(updated.getUpdateTime());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            ApiDefinitionEntity nonExistent = new ApiDefinitionEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = apiDefinitionMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            int affected = apiDefinitionMapper.update(
                    null,
                    Wrappers.<ApiDefinitionEntity>lambdaUpdate()
                            .set(ApiDefinitionEntity::getStatus, "DEPRECATED")
                            .eq(ApiDefinitionEntity::getMethod, "GET")
            );
            assertEquals(1, affected);

            ApiDefinitionEntity updated = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("DEPRECATED", updated.getStatus());

            ApiDefinitionEntity unchanged = apiDefinitionMapper.selectById(createUserApi.getId());
            assertEquals("PUBLISHED", unchanged.getStatus());
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
            apiDefinitionMapper.insert(getUsersApi);
            apiDefinitionMapper.insert(createUserApi);
            apiDefinitionMapper.insert(deleteUserApi);
        }

        @Test
        @DisplayName("逻辑删除接口定义应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = apiDefinitionMapper.deleteById(getUsersApi.getId());
            assertEquals(1, affected);

            List<ApiDefinitionEntity> all = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getId, getUsersApi.getId())
            );
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            apiDefinitionMapper.deleteById(getUsersApi.getId());

            ApiDefinitionEntity found = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = apiDefinitionMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(getUsersApi.getId(), createUserApi.getId());
            int affected = apiDefinitionMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            List<ApiDefinitionEntity> remaining = apiDefinitionMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            int affected = apiDefinitionMapper.delete(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getMethod, "GET")
            );
            assertEquals(1, affected);

            assertNull(apiDefinitionMapper.selectById(getUsersApi.getId()));
            assertNotNull(apiDefinitionMapper.selectById(createUserApi.getId()));
            assertNotNull(apiDefinitionMapper.selectById(deleteUserApi.getId()));
        }
    }

    // ========================================================================
    // 业务语义 — 分组与方法
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 分组与方法")
    class GroupAndMethodSemantics {

        private ApiDefinitionEntity orderGetApi;
        private ApiDefinitionEntity orderPostApi;

        @BeforeEach
        void insertData() {
            apiDefinitionMapper.insert(getUsersApi);
            apiDefinitionMapper.insert(createUserApi);
            apiDefinitionMapper.insert(deleteUserApi);

            orderGetApi = new ApiDefinitionEntity();
            orderGetApi.setProjectId(testProjectId());
            orderGetApi.setGroupId(3001L);
            orderGetApi.setName("获取订单列表");
            orderGetApi.setPath("/api/orders");
            orderGetApi.setMethod("GET");
            orderGetApi.setStatus("PUBLISHED");
            orderGetApi.setVersion("1.0.0");
            orderGetApi.setTags(Arrays.asList("订单", "查询"));
            orderGetApi.setDescription("获取订单列表");
            orderGetApi.setProtocol("HTTP");
            orderGetApi.setHost("localhost:8080");
            orderGetApi.setContentType("application/json");
            orderGetApi.setDeleted(0);
            apiDefinitionMapper.insert(orderGetApi);

            orderPostApi = new ApiDefinitionEntity();
            orderPostApi.setProjectId(testProjectId());
            orderPostApi.setGroupId(3001L);
            orderPostApi.setName("创建订单");
            orderPostApi.setPath("/api/orders");
            orderPostApi.setMethod("POST");
            orderPostApi.setStatus("DRAFT");
            orderPostApi.setVersion("1.0.0");
            orderPostApi.setTags(Arrays.asList("订单", "创建"));
            orderPostApi.setDescription("创建新订单");
            orderPostApi.setProtocol("HTTP");
            orderPostApi.setHost("localhost:8080");
            orderPostApi.setContentType("application/json");
            orderPostApi.setDeleted(0);
            apiDefinitionMapper.insert(orderPostApi);
        }

        @Test
        @DisplayName("同一分组下可存在多个不同方法的接口")
        void sameGroupCanHaveDifferentMethods() {
            List<ApiDefinitionEntity> groupApis = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getGroupId, 3001L)
            );
            assertEquals(2, groupApis.size());
            assertTrue(groupApis.stream().anyMatch(a -> "GET".equals(a.getMethod())));
            assertTrue(groupApis.stream().anyMatch(a -> "POST".equals(a.getMethod())));
        }

        @Test
        @DisplayName("不同分组下相同路径的接口应独立存在")
        void samePathInDifferentGroupsShouldBeIndependent() {
            List<ApiDefinitionEntity> getApis = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getPath, "/api/users")
                            .eq(ApiDefinitionEntity::getMethod, "GET")
            );
            assertEquals(1, getApis.size());
            assertEquals(1001L, getApis.get(0).getGroupId());
        }

        @Test
        @DisplayName("查询同一路径下所有方法的接口应返回全部")
        void selectAllMethodsForSamePathShouldReturnAll() {
            List<ApiDefinitionEntity> orders = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getPath, "/api/orders")
            );
            assertEquals(2, orders.size());
        }

        @Test
        @DisplayName("跨分组查询 DRAFT 状态的接口应返回正确结果")
        void selectDraftAcrossGroupsShouldReturnCorrect() {
            List<ApiDefinitionEntity> drafts = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getStatus, "DRAFT")
            );
            assertEquals(2, drafts.size());
        }
    }

    // ========================================================================
    // 业务语义 — 版本
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 版本")
    class VersionSemantics {

        private ApiDefinitionEntity getUsersV2Api;

        @BeforeEach
        void insertData() {
            apiDefinitionMapper.insert(getUsersApi);

            // 同一接口的 v2 版本
            getUsersV2Api = new ApiDefinitionEntity();
            getUsersV2Api.setProjectId(testProjectId());
            getUsersV2Api.setGroupId(1001L);
            getUsersV2Api.setName("获取用户列表");
            getUsersV2Api.setPath("/api/users");
            getUsersV2Api.setMethod("GET");
            getUsersV2Api.setStatus("PUBLISHED");
            getUsersV2Api.setVersion("2.0.0");
            getUsersV2Api.setTags(Arrays.asList("用户", "查询"));
            getUsersV2Api.setDescription("获取用户列表 v2");
            getUsersV2Api.setProtocol("HTTP");
            getUsersV2Api.setHost("localhost:8080");
            getUsersV2Api.setContentType("application/json");
            getUsersV2Api.setDeleted(0);
            apiDefinitionMapper.insert(getUsersV2Api);
        }

        @Test
        @DisplayName("同一接口可以存在多个版本")
        void sameApiCanHaveMultipleVersions() {
            List<ApiDefinitionEntity> getUsers = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getName, "获取用户列表")
                            .eq(ApiDefinitionEntity::getMethod, "GET")
                            .eq(ApiDefinitionEntity::getPath, "/api/users")
            );
            assertEquals(2, getUsers.size());
        }

        @Test
        @DisplayName("按版本号查询应返回正确版本的接口")
        void selectByVersionShouldReturnCorrectVersion() {
            List<ApiDefinitionEntity> v2Apis = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getVersion, "2.0.0")
            );
            assertEquals(1, v2Apis.size());
            assertEquals("2.0.0", v2Apis.get(0).getVersion());
        }

        @Test
        @DisplayName("不同版本可以有不同的状态")
        void differentVersionsCanHaveDifferentStatus() {
            assertEquals("DRAFT", getUsersApi.getStatus());
            assertEquals("PUBLISHED", getUsersV2Api.getStatus());
        }
    }

    // ========================================================================
    // 业务语义 — 标签（tags JSON 处理）
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 标签（JSON 类型处理器）")
    class TagsSemantics {

        @Test
        @DisplayName("空的 tags 列表应正确存储与读取")
        void emptyTagsShouldBeStoredCorrectly() {
            getUsersApi.setTags(List.of());
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(saved.getTags());
            assertTrue(saved.getTags().isEmpty());
        }

        @Test
        @DisplayName("null tags 应正确存储与读取")
        void nullTagsShouldBeStoredCorrectly() {
            getUsersApi.setTags(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(saved.getTags());
        }

        @Test
        @DisplayName("多个标签应正确存储与读取")
        void multipleTagsShouldBeStoredCorrectly() {
            getUsersApi.setTags(Arrays.asList("标签A", "标签B", "标签C", "标签D", "标签E"));
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNotNull(saved.getTags());
            assertEquals(5, saved.getTags().size());
            assertTrue(saved.getTags().containsAll(Arrays.asList("标签A", "标签B", "标签C", "标签D", "标签E")));
        }

        @Test
        @DisplayName("更新 tags 应为全量替换")
        void updateTagsShouldReplaceCompletely() {
            apiDefinitionMapper.insert(getUsersApi);
            assertNotNull(getUsersApi.getId());
            assertTrue(getUsersApi.getTags().contains("用户"));

            getUsersApi.setTags(Arrays.asList("新标签1", "新标签2"));
            apiDefinitionMapper.updateById(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(2, saved.getTags().size());
            assertTrue(saved.getTags().contains("新标签1"));
            assertFalse(saved.getTags().contains("用户"));
        }
    }

    // ========================================================================
    // 边界条件
    // ========================================================================

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

        @Test
        @DisplayName("接口名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "测试接口" + "A".repeat(100);
            getUsersApi.setName(longName);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("接口路径为超长字符串时插入与查询应正确")
        void veryLongPathShouldBeStoredCorrectly() {
            String longPath = "/api/" + "a".repeat(200);
            getUsersApi.setPath(longPath);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(longPath, saved.getPath());
        }

        @Test
        @DisplayName("接口描述为超长字符串时插入与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            getUsersApi.setDescription(longDesc);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals(longDesc, saved.getDescription());
        }

        @Test
        @DisplayName("description 为 null 时插入与查询应正确")
        void nullDescriptionShouldBeStoredCorrectly() {
            getUsersApi.setDescription(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(saved.getDescription());
        }

        @Test
        @DisplayName("description 为空字符串时插入与查询应正确")
        void emptyDescriptionShouldBeStoredCorrectly() {
            getUsersApi.setDescription("");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("", saved.getDescription());
        }

        @Test
        @DisplayName("host 为 null 时插入与查询应正确")
        void nullHostShouldBeStoredCorrectly() {
            getUsersApi.setHost(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(saved.getHost());
        }

        @Test
        @DisplayName("host 为空字符串时插入与查询应正确")
        void emptyHostShouldBeStoredCorrectly() {
            getUsersApi.setHost("");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("", saved.getHost());
        }

        @Test
        @DisplayName("contentType 为 null 时插入与查询应正确")
        void nullContentTypeShouldBeStoredCorrectly() {
            getUsersApi.setContentType(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(saved.getContentType());
        }

        @Test
        @DisplayName("contentType 为 multipart/form-data 时插入与查询应正确")
        void multipartContentTypeShouldBeStoredCorrectly() {
            getUsersApi.setContentType("multipart/form-data");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("multipart/form-data", saved.getContentType());
        }

        @Test
        @DisplayName("path 含路径参数占位符时插入与查询应正确")
        void pathWithPlaceholdersShouldBeStoredCorrectly() {
            getUsersApi.setPath("/api/users/{userId}/orders/{orderId}");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("/api/users/{userId}/orders/{orderId}", saved.getPath());
        }

        @Test
        @DisplayName("version 支持含后缀的语义化版本号")
        void versionWithSuffixShouldBeStoredCorrectly() {
            getUsersApi.setVersion("1.0.0-beta.1");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("1.0.0-beta.1", saved.getVersion());
        }

        @Test
        @DisplayName("method 为 HEAD 时插入与查询应正确")
        void headMethodShouldBeStoredCorrectly() {
            getUsersApi.setMethod("HEAD");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("HEAD", saved.getMethod());
        }

        @Test
        @DisplayName("method 为 OPTIONS 时插入与查询应正确")
        void optionsMethodShouldBeStoredCorrectly() {
            getUsersApi.setMethod("OPTIONS");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("OPTIONS", saved.getMethod());
        }

        @Test
        @DisplayName("status 为 DEPRECATED 时插入与查询应正确")
        void deprecatedStatusShouldBeStoredCorrectly() {
            getUsersApi.setStatus("DEPRECATED");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("DEPRECATED", saved.getStatus());
        }

        @Test
        @DisplayName("protocol 为 HTTPS 时插入与查询应正确")
        void httpsProtocolShouldBeStoredCorrectly() {
            getUsersApi.setProtocol("HTTPS");
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertEquals("HTTPS", saved.getProtocol());
        }

        @Test
        @DisplayName("同一项目下允许同名接口存在")
        void duplicateApiNameInSameProjectShouldBeAllowed() {
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity duplicate = new ApiDefinitionEntity();
            duplicate.setProjectId(testProjectId());
            duplicate.setGroupId(1001L);
            duplicate.setName("获取用户列表");
            duplicate.setPath("/api/users/v2");
            duplicate.setMethod("GET");
            duplicate.setStatus("DRAFT");
            duplicate.setVersion("1.0.0");
            duplicate.setProtocol("HTTP");
            duplicate.setContentType("application/json");
            duplicate.setDeleted(0);
            apiDefinitionMapper.insert(duplicate);

            List<ApiDefinitionEntity> result = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getName, "获取用户列表")
                            .eq(ApiDefinitionEntity::getProjectId, testProjectId())
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("不同项目下的接口应互不干扰")
        void apisInDifferentProjectsShouldBeIndependent() {
            apiDefinitionMapper.insert(getUsersApi);

            Long anotherProjectId = 2002L;
            ApiDefinitionEntity otherProjectApi = new ApiDefinitionEntity();
            otherProjectApi.setProjectId(anotherProjectId);
            otherProjectApi.setGroupId(1001L);
            otherProjectApi.setName("其他项目接口");
            otherProjectApi.setPath("/api/other");
            otherProjectApi.setMethod("GET");
            otherProjectApi.setStatus("DRAFT");
            otherProjectApi.setVersion("1.0.0");
            otherProjectApi.setProtocol("HTTP");
            otherProjectApi.setContentType("application/json");
            otherProjectApi.setDeleted(0);
            apiDefinitionMapper.insert(otherProjectApi);

            List<ApiDefinitionEntity> projectApis = apiDefinitionMapper.selectList(
                    Wrappers.<ApiDefinitionEntity>lambdaQuery()
                            .eq(ApiDefinitionEntity::getProjectId, testProjectId())
            );
            assertTrue(projectApis.stream().allMatch(a -> a.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("groupId 为 null 时插入与查询应正确")
        void nullGroupIdShouldBeStoredCorrectly() {
            getUsersApi.setGroupId(null);
            apiDefinitionMapper.insert(getUsersApi);

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(getUsersApi.getId());
            assertNull(saved.getGroupId());
        }
    }
}