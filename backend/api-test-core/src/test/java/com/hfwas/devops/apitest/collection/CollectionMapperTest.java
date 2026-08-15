package com.hfwas.devops.apitest.collection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionMapper;
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
 * CollectionMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及集合特有的业务语义（项目隔离、排序、逻辑删除）。
 *
 * @author hfwas
 */
@DisplayName("CollectionMapper — 集合数据访问测试")
class CollectionMapperTest extends BaseApiTest {

    @Autowired
    private CollectionMapper collectionMapper;

    private CollectionEntity collectionA;
    private CollectionEntity collectionB;
    private CollectionEntity collectionC;

    @BeforeEach
    void setUp() {
        // 构造集合 A
        collectionA = new CollectionEntity();
        collectionA.setProjectId(testProjectId());
        collectionA.setName("用户管理接口集合");
        collectionA.setDescription("包含用户管理模块的所有接口");
        collectionA.setSortOrder(1);
        collectionA.setDeleted(0);

        // 构造集合 B
        collectionB = new CollectionEntity();
        collectionB.setProjectId(testProjectId());
        collectionB.setName("订单管理接口集合");
        collectionB.setDescription("包含订单管理模块的所有接口");
        collectionB.setSortOrder(2);
        collectionB.setDeleted(0);

        // 构造集合 C
        collectionC = new CollectionEntity();
        collectionC.setProjectId(testProjectId());
        collectionC.setName("商品管理接口集合");
        collectionC.setDescription("包含商品管理模块的所有接口");
        collectionC.setSortOrder(3);
        collectionC.setDeleted(0);
    }

    // ========================================================================
    // 条件构造器快捷方法
    // ========================================================================

    private LambdaQueryWrapper<CollectionEntity> query() {
        return Wrappers.lambdaQuery();
    }

    private LambdaUpdateWrapper<CollectionEntity> update() {
        return Wrappers.lambdaUpdate();
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入集合应成功并返回自增 ID")
        void insertShouldSucceed() {
            int affected = collectionMapper.insert(collectionA);
            assertEquals(1, affected);
            assertNotNull(collectionA.getId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            List<CollectionEntity> batch = Arrays.asList(collectionA, collectionB, collectionC);
            for (CollectionEntity c : batch) {
                int affected = collectionMapper.insert(c);
                assertEquals(1, affected);
                assertNotNull(c.getId());
            }
        }

        @Test
        @DisplayName("插入时应自动填充 createBy、createTime、updateBy、updateTime")
        void insertShouldAutoFillAuditFields() {
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            collectionA.setDeleted(null);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertNotNull(saved);
            assertEquals(0, saved.getDeleted());
        }

        @Test
        @DisplayName("插入时 name 为空字符串应能成功")
        void insertWithEmptyNameShouldSucceed() {
            collectionA.setName("");
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertNotNull(saved);
            assertEquals("", saved.getName());
        }

        @Test
        @DisplayName("插入时 description 为 null 应能成功")
        void insertWithNullDescriptionShouldSucceed() {
            collectionA.setDescription(null);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertNotNull(saved);
            assertNull(saved.getDescription());
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
            collectionMapper.insert(collectionA);
            collectionMapper.insert(collectionB);
            collectionMapper.insert(collectionC);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确集合")
        void selectByIdShouldReturnCorrectCollection() {
            CollectionEntity found = collectionMapper.selectById(collectionA.getId());
            assertNotNull(found);
            assertEquals(collectionA.getId(), found.getId());
            assertEquals("用户管理接口集合", found.getName());
            assertEquals(testProjectId(), found.getProjectId());
            assertEquals(1, found.getSortOrder());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            CollectionEntity found = collectionMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有集合应返回全部记录（含逻辑删除的）")
        void selectListAllShouldReturnAllCollections() {
            List<CollectionEntity> all = collectionMapper.selectList(null);
            assertNotNull(all);
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 projectId 条件查询应返回正确结果")
        void selectByProjectIdShouldReturnCorrectCollections() {
            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(c -> c.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("根据名称精确查询应返回正确结果")
        void selectByNameShouldReturnCorrectCollection() {
            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getName, "用户管理接口集合")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("用户管理接口集合", result.get(0).getName());
        }

        @Test
        @DisplayName("按名称模糊查询应返回匹配的集合")
        void selectByNameLikeShouldReturnMatchingCollections() {
            List<CollectionEntity> result = collectionMapper.selectList(
                    query().like(CollectionEntity::getName, "管理")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(c -> c.getName().contains("管理")));
        }

        @Test
        @DisplayName("按 sortOrder 排序查询应返回有序结果")
        void selectOrderBySortOrderShouldReturnOrderedResults() {
            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
                            .orderByAsc(CollectionEntity::getSortOrder)
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = collectionMapper.selectCount(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertNotNull(count);
            assertTrue(count >= 3);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(collectionA.getId(), collectionB.getId());
            List<CollectionEntity> result = collectionMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("selectOne 应返回唯一匹配的记录")
        void selectOneShouldReturnUniqueMatch() {
            CollectionEntity result = collectionMapper.selectOne(
                    query().eq(CollectionEntity::getName, "用户管理接口集合")
            );
            assertNotNull(result);
            assertEquals(collectionA.getId(), result.getId());
        }

        @Test
        @DisplayName("selectOne 条件不匹配时应返回 null")
        void selectOneNoMatchShouldReturnNull() {
            CollectionEntity result = collectionMapper.selectOne(
                    query().eq(CollectionEntity::getName, "不存在的集合名称")
            );
            assertNull(result);
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
            collectionMapper.insert(collectionA);
            collectionMapper.insert(collectionB);
        }

        @Test
        @DisplayName("更新集合名称应成功")
        void updateNameShouldSucceed() {
            collectionA.setName("用户管理接口集合V2");
            int affected = collectionMapper.updateById(collectionA);
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals("用户管理接口集合V2", updated.getName());
        }

        @Test
        @DisplayName("更新集合描述应成功")
        void updateDescriptionShouldSucceed() {
            collectionA.setDescription("更新后的描述信息");
            int affected = collectionMapper.updateById(collectionA);
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals("更新后的描述信息", updated.getDescription());
        }

        @Test
        @DisplayName("更新集合排序序号应成功")
        void updateSortOrderShouldSucceed() {
            collectionA.setSortOrder(99);
            int affected = collectionMapper.updateById(collectionA);
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals(99, updated.getSortOrder());
        }

        @Test
        @DisplayName("更新时 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillUpdateFields() {
            collectionA.setName("更新名称");
            collectionMapper.updateById(collectionA);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals(testUserId(), updated.getUpdateBy());
            assertNotNull(updated.getUpdateTime());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            CollectionEntity nonExistent = new CollectionEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = collectionMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            int affected = collectionMapper.update(
                    null,
                    update().set(CollectionEntity::getName, "批量更新名称")
                            .eq(CollectionEntity::getName, "订单管理接口集合")
            );
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionB.getId());
            assertEquals("批量更新名称", updated.getName());

            CollectionEntity unchanged = collectionMapper.selectById(collectionA.getId());
            assertEquals("用户管理接口集合", unchanged.getName());
        }

        @Test
        @DisplayName("更新 description 为 null 应成功")
        void updateDescriptionToNullShouldSucceed() {
            int affected = collectionMapper.update(
                    new LambdaUpdateWrapper<CollectionEntity>()
                            .eq(CollectionEntity::getId, collectionA.getId())
                            .set(CollectionEntity::getDescription, null));
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertNull(updated.getDescription());
        }

        @Test
        @DisplayName("更新 description 为空字符串应成功")
        void updateDescriptionToEmptyShouldSucceed() {
            collectionA.setDescription("");
            int affected = collectionMapper.updateById(collectionA);
            assertEquals(1, affected);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals("", updated.getDescription());
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
            collectionMapper.insert(collectionA);
            collectionMapper.insert(collectionB);
            collectionMapper.insert(collectionC);
        }

        @Test
        @DisplayName("逻辑删除集合应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = collectionMapper.deleteById(collectionA.getId());
            assertEquals(1, affected);

            List<CollectionEntity> all = collectionMapper.selectList(
                    query().eq(CollectionEntity::getId, collectionA.getId())
            );
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            collectionMapper.deleteById(collectionA.getId());

            CollectionEntity found = collectionMapper.selectById(collectionA.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = collectionMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(collectionA.getId(), collectionB.getId());
            int affected = collectionMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            List<CollectionEntity> remaining = collectionMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            int affected = collectionMapper.delete(
                    query().eq(CollectionEntity::getName, "用户管理接口集合")
            );
            assertEquals(1, affected);

            assertNull(collectionMapper.selectById(collectionA.getId()));
            assertNotNull(collectionMapper.selectById(collectionB.getId()));
            assertNotNull(collectionMapper.selectById(collectionC.getId()));
        }

        @Test
        @DisplayName("多次逻辑删除同一记录应返回 0")
        void deleteAlreadyDeletedShouldReturnZero() {
            collectionMapper.deleteById(collectionA.getId());
            int affected = collectionMapper.deleteById(collectionA.getId());
            assertEquals(0, affected);
        }
    }

    // ========================================================================
    // 业务语义 — 项目隔离
    // ========================================================================

    @Nested
    @DisplayName("项目隔离 — 业务语义")
    class ProjectIsolation {

        @Test
        @DisplayName("不同项目下的集合应互不干扰")
        void collectionsInDifferentProjectsShouldBeIndependent() {
            collectionMapper.insert(collectionA);

            Long anotherProjectId = 2002L;
            CollectionEntity otherProjectCollection = new CollectionEntity();
            otherProjectCollection.setProjectId(anotherProjectId);
            otherProjectCollection.setName("其他项目集合");
            otherProjectCollection.setSortOrder(1);
            otherProjectCollection.setDeleted(0);
            collectionMapper.insert(otherProjectCollection);

            List<CollectionEntity> projectCollections = collectionMapper.selectList(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertTrue(projectCollections.stream().allMatch(c -> c.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("按 projectId 删除应仅删除指定项目的集合")
        void deleteByProjectIdShouldOnlyDeleteMatchingProject() {
            collectionMapper.insert(collectionA);

            Long anotherProjectId = 2002L;
            CollectionEntity otherProjectCollection = new CollectionEntity();
            otherProjectCollection.setProjectId(anotherProjectId);
            otherProjectCollection.setName("其他项目集合");
            otherProjectCollection.setSortOrder(1);
            otherProjectCollection.setDeleted(0);
            collectionMapper.insert(otherProjectCollection);

            int affected = collectionMapper.delete(
                    query().eq(CollectionEntity::getProjectId, anotherProjectId)
            );
            assertEquals(1, affected);

            assertNotNull(collectionMapper.selectById(collectionA.getId()));
            assertNull(collectionMapper.selectById(otherProjectCollection.getId()));
        }

        @Test
        @DisplayName("插入 projectId 为 null 的记录应抛出异常")
        void selectNullProjectIdShouldReturnEmpty() {
            CollectionEntity nullProjectCollection = new CollectionEntity();
            nullProjectCollection.setProjectId(null);
            nullProjectCollection.setName("无项目集合");
            nullProjectCollection.setSortOrder(1);
            nullProjectCollection.setDeleted(0);

            assertThrows(Exception.class, () ->
                    collectionMapper.insert(nullProjectCollection));
        }
    }

    // ========================================================================
    // 业务语义 — 排序
    // ========================================================================

    @Nested
    @DisplayName("集合排序 — 业务语义")
    class CollectionSorting {

        @Test
        @DisplayName("同一项目下按 sortOrder 升序查询应返回有序结果")
        void selectBySortOrderAscShouldReturnOrdered() {
            List<CollectionEntity> collections = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                CollectionEntity c = new CollectionEntity();
                c.setProjectId(testProjectId());
                c.setName("集合" + i);
                c.setSortOrder(i);
                c.setDeleted(0);
                collectionMapper.insert(c);
                collections.add(c);
            }

            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
                            .orderByAsc(CollectionEntity::getSortOrder)
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
                CollectionEntity c = new CollectionEntity();
                c.setProjectId(testProjectId());
                c.setName("集合" + i);
                c.setSortOrder(i);
                c.setDeleted(0);
                collectionMapper.insert(c);
            }

            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getProjectId, testProjectId())
                            .orderByDesc(CollectionEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() >= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("相同 sortOrder 的集合应能共存")
        void collectionsWithSameSortOrderShouldCoexist() {
            CollectionEntity c1 = new CollectionEntity();
            c1.setProjectId(testProjectId());
            c1.setName("集合A");
            c1.setSortOrder(1);
            c1.setDeleted(0);
            collectionMapper.insert(c1);

            CollectionEntity c2 = new CollectionEntity();
            c2.setProjectId(testProjectId());
            c2.setName("集合B");
            c2.setSortOrder(1);
            c2.setDeleted(0);
            collectionMapper.insert(c2);

            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getSortOrder, 1)
                            .eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("更新 sortOrder 应正确持久化")
        void updateSortOrderShouldPersistCorrectly() {
            collectionMapper.insert(collectionA);

            collectionA.setSortOrder(50);
            collectionMapper.updateById(collectionA);

            CollectionEntity updated = collectionMapper.selectById(collectionA.getId());
            assertEquals(50, updated.getSortOrder());
        }
    }

    // ========================================================================
    // 边界条件
    // ========================================================================

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

        @Test
        @DisplayName("集合名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "测试集合" + "A".repeat(100);
            collectionA.setName(longName);
            collectionA.setSortOrder(1);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("集合描述为超长字符串时插入与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            collectionA.setDescription(longDesc);
            collectionA.setSortOrder(1);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(longDesc, saved.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为最小值时插入与查询应正确")
        void minSortOrderShouldBeStoredCorrectly() {
            collectionA.setSortOrder(Integer.MIN_VALUE);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(Integer.MIN_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时插入与查询应正确")
        void maxSortOrderShouldBeStoredCorrectly() {
            collectionA.setSortOrder(Integer.MAX_VALUE);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(Integer.MAX_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为 0 时插入与查询应正确")
        void zeroSortOrderShouldBeStoredCorrectly() {
            collectionA.setSortOrder(0);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(0, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为负数时插入与查询应正确")
        void negativeSortOrderShouldBeStoredCorrectly() {
            collectionA.setSortOrder(-1);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals(-1, saved.getSortOrder());
        }

        @Test
        @DisplayName("同一项目下允许同名集合存在")
        void duplicateCollectionNameInSameProjectShouldBeAllowed() {
            collectionMapper.insert(collectionA);

            CollectionEntity duplicate = new CollectionEntity();
            duplicate.setProjectId(testProjectId());
            duplicate.setName("用户管理接口集合");
            duplicate.setSortOrder(2);
            duplicate.setDeleted(0);
            collectionMapper.insert(duplicate);

            List<CollectionEntity> result = collectionMapper.selectList(
                    query().eq(CollectionEntity::getName, "用户管理接口集合")
                            .eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("不同项目下同名集合应互不干扰")
        void sameNameInDifferentProjectsShouldBeIndependent() {
            collectionMapper.insert(collectionA);

            Long anotherProjectId = 2002L;
            CollectionEntity otherProjectCollection = new CollectionEntity();
            otherProjectCollection.setProjectId(anotherProjectId);
            otherProjectCollection.setName("用户管理接口集合");
            otherProjectCollection.setSortOrder(1);
            otherProjectCollection.setDeleted(0);
            collectionMapper.insert(otherProjectCollection);

            List<CollectionEntity> projectOneCollections = collectionMapper.selectList(
                    query().eq(CollectionEntity::getName, "用户管理接口集合")
                            .eq(CollectionEntity::getProjectId, testProjectId())
            );
            assertEquals(1, projectOneCollections.size());
        }

        @Test
        @DisplayName("description 为 null 时插入与查询应正确")
        void nullDescriptionShouldBeStoredCorrectly() {
            collectionA.setDescription(null);
            collectionA.setSortOrder(1);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertNull(saved.getDescription());
        }

        @Test
        @DisplayName("description 为空字符串时插入与查询应正确")
        void emptyDescriptionShouldBeStoredCorrectly() {
            collectionA.setDescription("");
            collectionA.setSortOrder(1);
            collectionA.setDeleted(0);
            collectionMapper.insert(collectionA);

            CollectionEntity saved = collectionMapper.selectById(collectionA.getId());
            assertEquals("", saved.getDescription());
        }
    }
}