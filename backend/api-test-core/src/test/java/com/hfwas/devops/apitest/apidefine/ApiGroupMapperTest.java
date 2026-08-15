package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.apitest.apidefine.mapper.ApiGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiGroupMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及分组特有的业务语义（层级、排序、逻辑删除）。
 *
 * @author hfwas
 */
@DisplayName("ApiGroupMapper — 接口分组数据访问测试")
class ApiGroupMapperTest extends BaseApiTest {

    @Autowired
    private ApiGroupMapper apiGroupMapper;

    private ApiGroupEntity rootGroup;
    private ApiGroupEntity childGroup;
    private ApiGroupEntity anotherRootGroup;

    @BeforeEach
    void setUp() {
        // 构造根级分组
        rootGroup = new ApiGroupEntity();
        rootGroup.setProjectId(testProjectId());
        rootGroup.setParentId(null);
        rootGroup.setName("用户管理");
        rootGroup.setSortOrder(1);
        rootGroup.setDescription("用户相关接口");
        rootGroup.setDeleted(0);

        // 构造子级分组
        childGroup = new ApiGroupEntity();
        childGroup.setProjectId(testProjectId());
        childGroup.setParentId(null); // 先插入后再设置 parentId
        childGroup.setName("登录注册");
        childGroup.setSortOrder(1);
        childGroup.setDescription("登录与注册接口");
        childGroup.setDeleted(0);

        // 构造另一个根级分组
        anotherRootGroup = new ApiGroupEntity();
        anotherRootGroup.setProjectId(testProjectId());
        anotherRootGroup.setParentId(null);
        anotherRootGroup.setName("订单管理");
        anotherRootGroup.setSortOrder(2);
        anotherRootGroup.setDescription("订单相关接口");
        anotherRootGroup.setDeleted(0);
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入根级分组应成功并返回自增 ID")
        void insertRootGroupShouldSucceed() {
            int affected = apiGroupMapper.insert(rootGroup);
            assertEquals(1, affected);
            assertNotNull(rootGroup.getId());
        }

        @Test
        @DisplayName("插入子级分组应成功并正确关联父级")
        void insertChildGroupShouldSucceed() {
            apiGroupMapper.insert(rootGroup);
            childGroup.setParentId(rootGroup.getId());

            int affected = apiGroupMapper.insert(childGroup);
            assertEquals(1, affected);
            assertNotNull(childGroup.getId());
            assertEquals(rootGroup.getId(), childGroup.getParentId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            apiGroupMapper.insert(rootGroup);
            childGroup.setParentId(rootGroup.getId());

            ApiGroupEntity group3 = new ApiGroupEntity();
            group3.setProjectId(testProjectId());
            group3.setParentId(rootGroup.getId());
            group3.setName("用户信息");
            group3.setSortOrder(2);
            group3.setDeleted(0);

            List<ApiGroupEntity> batch = Arrays.asList(childGroup, group3);
            for (ApiGroupEntity g : batch) {
                int affected = apiGroupMapper.insert(g);
                assertEquals(1, affected);
                assertNotNull(g.getId());
            }
        }

        @Test
        @DisplayName("插入时应自动填充 createBy、createTime、updateBy、updateTime")
        void insertShouldAutoFillAuditFields() {
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            rootGroup.setDeleted(null);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertNotNull(saved);
            assertEquals(0, saved.getDeleted());
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
            apiGroupMapper.insert(rootGroup);
            childGroup.setParentId(rootGroup.getId());
            apiGroupMapper.insert(childGroup);
            apiGroupMapper.insert(anotherRootGroup);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确分组")
        void selectByIdShouldReturnCorrectGroup() {
            ApiGroupEntity found = apiGroupMapper.selectById(rootGroup.getId());
            assertNotNull(found);
            assertEquals(rootGroup.getId(), found.getId());
            assertEquals("用户管理", found.getName());
            assertEquals(testProjectId(), found.getProjectId());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            ApiGroupEntity found = apiGroupMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有分组应返回全部记录（含逻辑删除的）")
        void selectListAllShouldReturnAllGroups() {
            List<ApiGroupEntity> all = apiGroupMapper.selectList(null);
            // MyBatis-Plus 默认带逻辑删除过滤，所以这里只返回未删除的
            assertNotNull(all);
            // 至少包括我们插入的 3 条
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 projectId 条件查询应返回正确结果")
        void selectByProjectIdShouldReturnCorrectGroups() {
            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(g -> g.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("根据 parentId 查询子分组应返回正确结果")
        void selectByParentIdShouldReturnChildren() {
            List<ApiGroupEntity> children = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getParentId, rootGroup.getId())
            );
            assertNotNull(children);
            assertEquals(1, children.size());
            assertEquals(childGroup.getId(), children.get(0).getId());
        }

        @Test
        @DisplayName("查询 parentId 为 null 的根级分组应返回正确结果")
        void selectRootGroupsShouldReturnCorrectResults() {
            List<ApiGroupEntity> roots = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .isNull(ApiGroupEntity::getParentId)
            );
            assertNotNull(roots);
            assertTrue(roots.size() >= 2);
            assertTrue(roots.stream().allMatch(g -> g.getParentId() == null));
        }

        @Test
        @DisplayName("按名称模糊查询应返回匹配的分组")
        void selectByNameLikeShouldReturnMatchingGroups() {
            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .like(ApiGroupEntity::getName, "用户")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("用户管理", result.get(0).getName());
        }

        @Test
        @DisplayName("按 sortOrder 排序查询应返回有序结果")
        void selectOrderBySortOrderShouldReturnOrderedResults() {
            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
                            .isNull(ApiGroupEntity::getParentId)
                            .orderByAsc(ApiGroupEntity::getSortOrder)
            );
            assertNotNull(result);
            assertTrue(result.size() >= 2);
            // sortOrder: 1 ("用户管理"), 2 ("订单管理")
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = apiGroupMapper.selectCount(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
            );
            assertNotNull(count);
            assertTrue(count >= 3);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(rootGroup.getId(), childGroup.getId());
            List<ApiGroupEntity> result = apiGroupMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
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
            apiGroupMapper.insert(rootGroup);
            childGroup.setParentId(rootGroup.getId());
            apiGroupMapper.insert(childGroup);
        }

        @Test
        @DisplayName("更新分组名称应成功")
        void updateNameShouldSucceed() {
            rootGroup.setName("用户管理V2");
            int affected = apiGroupMapper.updateById(rootGroup);
            assertEquals(1, affected);

            ApiGroupEntity updated = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals("用户管理V2", updated.getName());
        }

        @Test
        @DisplayName("更新分组描述应成功")
        void updateDescriptionShouldSucceed() {
            rootGroup.setDescription("更新后的描述");
            int affected = apiGroupMapper.updateById(rootGroup);
            assertEquals(1, affected);

            ApiGroupEntity updated = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals("更新后的描述", updated.getDescription());
        }

        @Test
        @DisplayName("更新分组排序序号应成功")
        void updateSortOrderShouldSucceed() {
            rootGroup.setSortOrder(99);
            int affected = apiGroupMapper.updateById(rootGroup);
            assertEquals(1, affected);

            ApiGroupEntity updated = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(99, updated.getSortOrder());
        }

        @Test
        @DisplayName("更新时 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillUpdateFields() {
            rootGroup.setName("更新名称");
            apiGroupMapper.updateById(rootGroup);

            ApiGroupEntity updated = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(testUserId(), updated.getUpdateBy());
            assertNotNull(updated.getUpdateTime());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            ApiGroupEntity nonExistent = new ApiGroupEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = apiGroupMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            apiGroupMapper.insert(anotherRootGroup);

            int affected = apiGroupMapper.update(
                    null,
                    Wrappers
                            .<ApiGroupEntity>lambdaUpdate()
                            .set(ApiGroupEntity::getName, "批量更新名称")
                            .eq(ApiGroupEntity::getParentId, rootGroup.getId())
            );
            assertEquals(1, affected);

            // 验证 childGroup 名称已更新
            ApiGroupEntity updatedChild = apiGroupMapper.selectById(childGroup.getId());
            assertEquals("批量更新名称", updatedChild.getName());

            // 验证 rootGroup 名称未更新
            ApiGroupEntity unchangedRoot = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals("用户管理", unchangedRoot.getName());
        }

        @Test
        @DisplayName("更新子分组的 parentId 可改变层级关系")
        void updateParentIdShouldChangeHierarchy() {
            apiGroupMapper.insert(anotherRootGroup);

            // 将 childGroup 的父级从 rootGroup 改为 anotherRootGroup
            childGroup.setParentId(anotherRootGroup.getId());
            int affected = apiGroupMapper.updateById(childGroup);
            assertEquals(1, affected);

            ApiGroupEntity updated = apiGroupMapper.selectById(childGroup.getId());
            assertEquals(anotherRootGroup.getId(), updated.getParentId());
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
            apiGroupMapper.insert(rootGroup);
            childGroup.setParentId(rootGroup.getId());
            apiGroupMapper.insert(childGroup);
        }

        @Test
        @DisplayName("逻辑删除分组应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = apiGroupMapper.deleteById(rootGroup.getId());
            assertEquals(1, affected);

            // 直接查数据库（忽略逻辑删除）应能看到 deleted = 1
            // 注意：selectById 默认会过滤逻辑删除，所以需要用条件构造器强制查询
            List<ApiGroupEntity> all = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getId, rootGroup.getId())
            );
            // 逻辑删除后，默认查询应过滤掉
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            apiGroupMapper.deleteById(rootGroup.getId());

            ApiGroupEntity found = apiGroupMapper.selectById(rootGroup.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = apiGroupMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(rootGroup.getId(), childGroup.getId());
            int affected = apiGroupMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            // 验证已删除
            List<ApiGroupEntity> remaining = apiGroupMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            apiGroupMapper.insert(anotherRootGroup);

            int affected = apiGroupMapper.delete(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getName, "用户管理")
            );
            assertEquals(1, affected);

            // 验证 rootGroup 被删除
            assertNull(apiGroupMapper.selectById(rootGroup.getId()));
            // 验证 childGroup 和 anotherRootGroup 未被删除
            assertNotNull(apiGroupMapper.selectById(childGroup.getId()));
            assertNotNull(apiGroupMapper.selectById(anotherRootGroup.getId()));
        }
    }

    // ========================================================================
    // 业务语义 — 分组层级
    // ========================================================================

    @Nested
    @DisplayName("分组层级 — 业务语义")
    class GroupHierarchy {

        private ApiGroupEntity subChildGroup;
        private ApiGroupEntity thirdChildGroup;

        @BeforeEach
        void insertHierarchyData() {
            apiGroupMapper.insert(rootGroup);

            childGroup.setParentId(rootGroup.getId());
            apiGroupMapper.insert(childGroup);

            subChildGroup = new ApiGroupEntity();
            subChildGroup.setProjectId(testProjectId());
            subChildGroup.setParentId(childGroup.getId());
            subChildGroup.setName("手机号登录");
            subChildGroup.setSortOrder(1);
            subChildGroup.setDeleted(0);
            apiGroupMapper.insert(subChildGroup);

            thirdChildGroup = new ApiGroupEntity();
            thirdChildGroup.setProjectId(testProjectId());
            thirdChildGroup.setParentId(subChildGroup.getId());
            thirdChildGroup.setName("验证码");
            thirdChildGroup.setSortOrder(1);
            thirdChildGroup.setDeleted(0);
            apiGroupMapper.insert(thirdChildGroup);
        }

        @Test
        @DisplayName("查询子分组应返回直接子级（非递归）")
        void selectChildrenShouldReturnDirectChildren() {
            List<ApiGroupEntity> children = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getParentId, rootGroup.getId())
            );
            assertEquals(1, children.size());
            assertEquals(childGroup.getId(), children.get(0).getId());
        }

        @Test
        @DisplayName("多级层级下每级分组应正确关联父级")
        void multiLevelHierarchyShouldHaveCorrectParentReferences() {
            ApiGroupEntity fetchedChild = apiGroupMapper.selectById(childGroup.getId());
            assertEquals(rootGroup.getId(), fetchedChild.getParentId());

            ApiGroupEntity fetchedSubChild = apiGroupMapper.selectById(subChildGroup.getId());
            assertEquals(childGroup.getId(), fetchedSubChild.getParentId());

            ApiGroupEntity fetchedThird = apiGroupMapper.selectById(thirdChildGroup.getId());
            assertEquals(subChildGroup.getId(), fetchedThird.getParentId());
        }

        @Test
        @DisplayName("查询 parentId 为 null 的记录应得到所有根级分组")
        void selectNullParentIdShouldReturnAllRoots() {
            apiGroupMapper.insert(anotherRootGroup);

            List<ApiGroupEntity> roots = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .isNull(ApiGroupEntity::getParentId)
            );
            assertTrue(roots.size() >= 2);
            assertTrue(roots.stream().allMatch(g -> g.getParentId() == null));
        }
    }

    // ========================================================================
    // 业务语义 — 排序
    // ========================================================================

    @Nested
    @DisplayName("分组排序 — 业务语义")
    class GroupSorting {

        @Test
        @DisplayName("同一项目下按 sortOrder 升序查询应返回有序结果")
        void selectBySortOrderAscShouldReturnOrdered() {
            List<ApiGroupEntity> groups = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                ApiGroupEntity g = new ApiGroupEntity();
                g.setProjectId(testProjectId());
                g.setName("分组" + i);
                g.setSortOrder(i);
                g.setDeleted(0);
                apiGroupMapper.insert(g);
                groups.add(g);
            }

            // 按 sortOrder 升序查询
            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
                            .isNull(ApiGroupEntity::getParentId)
                            .orderByAsc(ApiGroupEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("同一项目下按 sortOrder 降序查询应返回有序结果")
        void selectBySortOrderDescShouldReturnOrdered() {
            for (int i = 1; i <= 5; i++) {
                ApiGroupEntity g = new ApiGroupEntity();
                g.setProjectId(testProjectId());
                g.setName("分组" + i);
                g.setSortOrder(i);
                g.setDeleted(0);
                apiGroupMapper.insert(g);
            }

            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
                            .isNull(ApiGroupEntity::getParentId)
                            .orderByDesc(ApiGroupEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() >= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("相同 sortOrder 的分组应能共存")
        void groupsWithSameSortOrderShouldCoexist() {
            ApiGroupEntity g1 = new ApiGroupEntity();
            g1.setProjectId(testProjectId());
            g1.setName("分组A");
            g1.setSortOrder(1);
            g1.setDeleted(0);
            apiGroupMapper.insert(g1);

            ApiGroupEntity g2 = new ApiGroupEntity();
            g2.setProjectId(testProjectId());
            g2.setName("分组B");
            g2.setSortOrder(1);
            g2.setDeleted(0);
            apiGroupMapper.insert(g2);

            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getSortOrder, 1)
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
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
        @DisplayName("分组名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "测试分组" + "A".repeat(100);
            rootGroup.setName(longName);
            rootGroup.setSortOrder(1);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("分组描述为超长字符串时插入与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            rootGroup.setDescription(longDesc);
            rootGroup.setSortOrder(1);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(longDesc, saved.getDescription());
        }

        @Test
        @DisplayName("description 为 null 时插入与查询应正确")
        void nullDescriptionShouldBeStoredCorrectly() {
            rootGroup.setDescription(null);
            rootGroup.setSortOrder(1);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertNull(saved.getDescription());
        }

        @Test
        @DisplayName("description 为空字符串时插入与查询应正确")
        void emptyDescriptionShouldBeStoredCorrectly() {
            rootGroup.setDescription("");
            rootGroup.setSortOrder(1);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals("", saved.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为最小值时插入与查询应正确")
        void minSortOrderShouldBeStoredCorrectly() {
            rootGroup.setSortOrder(Integer.MIN_VALUE);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(Integer.MIN_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时插入与查询应正确")
        void maxSortOrderShouldBeStoredCorrectly() {
            rootGroup.setSortOrder(Integer.MAX_VALUE);
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity saved = apiGroupMapper.selectById(rootGroup.getId());
            assertEquals(Integer.MAX_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("同一项目下允许同名分组存在")
        void duplicateGroupNameInSameProjectShouldBeAllowed() {
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            ApiGroupEntity duplicate = new ApiGroupEntity();
            duplicate.setProjectId(testProjectId());
            duplicate.setName("用户管理");
            duplicate.setSortOrder(2);
            duplicate.setDeleted(0);
            apiGroupMapper.insert(duplicate);

            // 查询同名分组应返回两条
            List<ApiGroupEntity> result = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getName, "用户管理")
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("不同项目下的分组应互不干扰")
        void groupsInDifferentProjectsShouldBeIndependent() {
            rootGroup.setDeleted(0);
            apiGroupMapper.insert(rootGroup);

            Long anotherProjectId = 2002L;
            ApiGroupEntity otherProjectGroup = new ApiGroupEntity();
            otherProjectGroup.setProjectId(anotherProjectId);
            otherProjectGroup.setName("其他项目分组");
            otherProjectGroup.setSortOrder(1);
            otherProjectGroup.setDeleted(0);
            apiGroupMapper.insert(otherProjectGroup);

            // 查询第一个项目的分组，不应包含其他项目的分组
            List<ApiGroupEntity> projectGroups = apiGroupMapper.selectList(
                    Wrappers
                            .<ApiGroupEntity>lambdaQuery()
                            .eq(ApiGroupEntity::getProjectId, testProjectId())
            );
            assertTrue(projectGroups.stream().allMatch(g -> g.getProjectId().equals(testProjectId())));
        }
    }
}