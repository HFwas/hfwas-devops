package com.hfwas.devops.apitest.environment;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.environment.entity.EnvironmentEntity;
import com.hfwas.devops.apitest.environment.mapper.EnvironmentMapper;
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
 * EnvironmentMapper 数据访问层测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖 MyBatis-Plus BaseMapper 提供的所有常用 CRUD 方法，
 * 以及环境特有的业务语义（项目隔离、排序、逻辑删除）。
 *
 * @author hfwas
 */
@DisplayName("EnvironmentMapper — 环境数据访问测试")
class EnvironmentMapperTest extends BaseApiTest {

    @Autowired
    private EnvironmentMapper environmentMapper;

    private EnvironmentEntity testEnv;
    private EnvironmentEntity stagingEnv;
    private EnvironmentEntity prodEnv;

    @BeforeEach
    void setUp() {
        testEnv = new EnvironmentEntity();
        testEnv.setProjectId(testProjectId());
        testEnv.setName("测试环境");
        testEnv.setDescription("用于接口功能测试");
        testEnv.setSortOrder(1);
        testEnv.setDeleted(0);

        stagingEnv = new EnvironmentEntity();
        stagingEnv.setProjectId(testProjectId());
        stagingEnv.setName("预发布环境");
        stagingEnv.setDescription("预发布验证环境");
        stagingEnv.setSortOrder(2);
        stagingEnv.setDeleted(0);

        prodEnv = new EnvironmentEntity();
        prodEnv.setProjectId(testProjectId());
        prodEnv.setName("生产环境");
        prodEnv.setDescription("线上生产环境");
        prodEnv.setSortOrder(3);
        prodEnv.setDeleted(0);
    }

    // ========================================================================
    // Insert 操作
    // ========================================================================

    @Nested
    @DisplayName("Insert 操作")
    class InsertOperations {

        @Test
        @DisplayName("插入环境应成功并返回自增 ID")
        void insertShouldSucceed() {
            int affected = environmentMapper.insert(testEnv);
            assertEquals(1, affected);
            assertNotNull(testEnv.getId());
        }

        @Test
        @DisplayName("批量插入多条记录应全部成功")
        void insertBatchShouldSucceed() {
            int affected1 = environmentMapper.insert(testEnv);
            int affected2 = environmentMapper.insert(stagingEnv);
            int affected3 = environmentMapper.insert(prodEnv);

            assertEquals(1, affected1);
            assertEquals(1, affected2);
            assertEquals(1, affected3);
            assertNotNull(testEnv.getId());
            assertNotNull(stagingEnv.getId());
            assertNotNull(prodEnv.getId());
        }

        @Test
        @DisplayName("插入时应自动填充 createBy、createTime、updateBy、updateTime")
        void insertShouldAutoFillAuditFields() {
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertNotNull(saved);
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("插入 deleted 默认值应为 0（未删除）")
        void insertDefaultDeletedShouldBeZero() {
            testEnv.setDeleted(null);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
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
            environmentMapper.insert(testEnv);
            environmentMapper.insert(stagingEnv);
            environmentMapper.insert(prodEnv);
        }

        @Test
        @DisplayName("按 ID 查询应返回正确的环境")
        void selectByIdShouldReturnCorrectEnv() {
            EnvironmentEntity found = environmentMapper.selectById(testEnv.getId());
            assertNotNull(found);
            assertEquals(testEnv.getId(), found.getId());
            assertEquals("测试环境", found.getName());
            assertEquals(testProjectId(), found.getProjectId());
        }

        @Test
        @DisplayName("按 ID 查询不存在的记录应返回 null")
        void selectByIdNonExistentShouldReturnNull() {
            EnvironmentEntity found = environmentMapper.selectById(99999L);
            assertNull(found);
        }

        @Test
        @DisplayName("查询所有环境应返回全部记录（未逻辑删除的）")
        void selectListAllShouldReturnAllEnvs() {
            List<EnvironmentEntity> all = environmentMapper.selectList(null);
            assertNotNull(all);
            assertTrue(all.size() >= 3);
        }

        @Test
        @DisplayName("根据 projectId 条件查询应返回正确结果")
        void selectByProjectIdShouldReturnCorrectEnvs() {
            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(e -> e.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("根据 name 精确查询应返回正确结果")
        void selectByNameShouldReturnCorrectEnv() {
            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getName, "生产环境")
            );
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("生产环境", result.get(0).getName());
        }

        @Test
        @DisplayName("根据 name 模糊查询应返回匹配的环境")
        void selectByNameLikeShouldReturnMatchingEnvs() {
            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .like(EnvironmentEntity::getName, "环境")
            );
            assertNotNull(result);
            assertTrue(result.size() >= 3);
            assertTrue(result.stream().allMatch(e -> e.getName().contains("环境")));
        }

        @Test
        @DisplayName("selectCount 应返回正确记录数")
        void selectCountShouldReturnCorrectNumber() {
            Long count = environmentMapper.selectCount(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
            );
            assertNotNull(count);
            assertTrue(count >= 3);
        }

        @Test
        @DisplayName("selectBatchIds 应返回指定 ID 列表的记录")
        void selectBatchIdsShouldReturnMatchingRecords() {
            List<Long> ids = Arrays.asList(testEnv.getId(), stagingEnv.getId());
            List<EnvironmentEntity> result = environmentMapper.selectBatchIds(ids);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("按 sortOrder 升序查询应返回有序结果")
        void selectOrderBySortOrderAscShouldReturnOrdered() {
            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
                            .orderByAsc(EnvironmentEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("按 sortOrder 降序查询应返回有序结果")
        void selectOrderBySortOrderDescShouldReturnOrdered() {
            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
                            .orderByDesc(EnvironmentEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() >= result.get(i).getSortOrder());
            }
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
            environmentMapper.insert(testEnv);
            environmentMapper.insert(stagingEnv);
        }

        @Test
        @DisplayName("更新环境名称应成功")
        void updateNameShouldSucceed() {
            testEnv.setName("测试环境V2");
            int affected = environmentMapper.updateById(testEnv);
            assertEquals(1, affected);

            EnvironmentEntity updated = environmentMapper.selectById(testEnv.getId());
            assertEquals("测试环境V2", updated.getName());
        }

        @Test
        @DisplayName("更新环境描述应成功")
        void updateDescriptionShouldSucceed() {
            testEnv.setDescription("更新后的环境描述");
            int affected = environmentMapper.updateById(testEnv);
            assertEquals(1, affected);

            EnvironmentEntity updated = environmentMapper.selectById(testEnv.getId());
            assertEquals("更新后的环境描述", updated.getDescription());
        }

        @Test
        @DisplayName("更新环境排序序号应成功")
        void updateSortOrderShouldSucceed() {
            testEnv.setSortOrder(99);
            int affected = environmentMapper.updateById(testEnv);
            assertEquals(1, affected);

            EnvironmentEntity updated = environmentMapper.selectById(testEnv.getId());
            assertEquals(99, updated.getSortOrder());
        }

        @Test
        @DisplayName("更新时 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillUpdateFields() {
            testEnv.setName("更新名称");
            environmentMapper.updateById(testEnv);

            EnvironmentEntity updated = environmentMapper.selectById(testEnv.getId());
            assertEquals(testUserId(), updated.getUpdateBy());
            assertNotNull(updated.getUpdateTime());
        }

        @Test
        @DisplayName("更新不存在的记录应返回影响行数为 0")
        void updateNonExistentShouldReturnZero() {
            EnvironmentEntity nonExistent = new EnvironmentEntity();
            nonExistent.setId(99999L);
            nonExistent.setName("不存在");
            int affected = environmentMapper.updateById(nonExistent);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("update 方法应仅更新条件匹配的记录")
        void updateWithConditionShouldOnlyUpdateMatchingRecords() {
            int affected = environmentMapper.update(
                    null,
                    Wrappers.<EnvironmentEntity>lambdaUpdate()
                            .set(EnvironmentEntity::getDescription, "批量更新描述")
                            .eq(EnvironmentEntity::getName, "测试环境")
            );
            assertEquals(1, affected);

            EnvironmentEntity updated = environmentMapper.selectById(testEnv.getId());
            assertEquals("批量更新描述", updated.getDescription());

            EnvironmentEntity unchanged = environmentMapper.selectById(stagingEnv.getId());
            assertEquals("预发布验证环境", unchanged.getDescription());
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
            environmentMapper.insert(testEnv);
            environmentMapper.insert(stagingEnv);
            environmentMapper.insert(prodEnv);
        }

        @Test
        @DisplayName("逻辑删除环境应设置 deleted = 1")
        void logicalDeleteShouldSetDeletedToOne() {
            int affected = environmentMapper.deleteById(testEnv.getId());
            assertEquals(1, affected);

            List<EnvironmentEntity> all = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getId, testEnv.getId())
            );
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            environmentMapper.deleteById(testEnv.getId());

            EnvironmentEntity found = environmentMapper.selectById(testEnv.getId());
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的记录应返回影响行数为 0")
        void deleteNonExistentShouldReturnZero() {
            int affected = environmentMapper.deleteById(99999L);
            assertEquals(0, affected);
        }

        @Test
        @DisplayName("批量删除应成功")
        void deleteBatchIdsShouldSucceed() {
            List<Long> ids = Arrays.asList(testEnv.getId(), stagingEnv.getId());
            int affected = environmentMapper.deleteBatchIds(ids);
            assertEquals(2, affected);

            List<EnvironmentEntity> remaining = environmentMapper.selectBatchIds(ids);
            assertTrue(remaining.isEmpty());
        }

        @Test
        @DisplayName("按条件删除应仅删除匹配的记录")
        void deleteWithConditionShouldOnlyDeleteMatching() {
            int affected = environmentMapper.delete(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getName, "测试环境")
            );
            assertEquals(1, affected);

            assertNull(environmentMapper.selectById(testEnv.getId()));
            assertNotNull(environmentMapper.selectById(stagingEnv.getId()));
            assertNotNull(environmentMapper.selectById(prodEnv.getId()));
        }
    }

    // ========================================================================
    // 业务语义 — 项目隔离与排序
    // ========================================================================

    @Nested
    @DisplayName("业务语义 — 项目隔离与排序")
    class BusinessSemantics {

        @Test
        @DisplayName("不同项目下的环境应互不干扰")
        void environmentsInDifferentProjectsShouldBeIndependent() {
            environmentMapper.insert(testEnv);

            Long anotherProjectId = 2002L;
            EnvironmentEntity otherProjectEnv = new EnvironmentEntity();
            otherProjectEnv.setProjectId(anotherProjectId);
            otherProjectEnv.setName("其他项目测试环境");
            otherProjectEnv.setSortOrder(1);
            otherProjectEnv.setDeleted(0);
            environmentMapper.insert(otherProjectEnv);

            List<EnvironmentEntity> projectEnvs = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
            );
            assertTrue(projectEnvs.stream().allMatch(e -> e.getProjectId().equals(testProjectId())));
        }

        @Test
        @DisplayName("同一项目下允许同名环境存在")
        void duplicateEnvNameInSameProjectShouldBeAllowed() {
            environmentMapper.insert(testEnv);

            EnvironmentEntity duplicate = new EnvironmentEntity();
            duplicate.setProjectId(testProjectId());
            duplicate.setName("测试环境");
            duplicate.setSortOrder(2);
            duplicate.setDeleted(0);
            environmentMapper.insert(duplicate);

            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getName, "测试环境")
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
            );
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("同一项目下按 sortOrder 升序查询应返回有序结果")
        void selectBySortOrderAscShouldReturnOrdered() {
            environmentMapper.insert(prodEnv);
            environmentMapper.insert(stagingEnv);
            environmentMapper.insert(testEnv);

            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
                            .orderByAsc(EnvironmentEntity::getSortOrder)
            );
            assertNotNull(result);
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getSortOrder() <= result.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("相同 sortOrder 的环境应能共存")
        void environmentsWithSameSortOrderShouldCoexist() {
            EnvironmentEntity env1 = new EnvironmentEntity();
            env1.setProjectId(testProjectId());
            env1.setName("环境A");
            env1.setSortOrder(1);
            env1.setDeleted(0);
            environmentMapper.insert(env1);

            EnvironmentEntity env2 = new EnvironmentEntity();
            env2.setProjectId(testProjectId());
            env2.setName("环境B");
            env2.setSortOrder(1);
            env2.setDeleted(0);
            environmentMapper.insert(env2);

            List<EnvironmentEntity> result = environmentMapper.selectList(
                    Wrappers.<EnvironmentEntity>lambdaQuery()
                            .eq(EnvironmentEntity::getSortOrder, 1)
                            .eq(EnvironmentEntity::getProjectId, testProjectId())
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
        @DisplayName("环境名称为超长字符串时插入与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "测试环境" + "A".repeat(100);
            testEnv.setName(longName);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals(longName, saved.getName());
        }

        @Test
        @DisplayName("环境描述为超长字符串时插入与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            testEnv.setDescription(longDesc);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals(longDesc, saved.getDescription());
        }

        @Test
        @DisplayName("description 为 null 时插入与查询应正确")
        void nullDescriptionShouldBeStoredCorrectly() {
            testEnv.setDescription(null);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertNull(saved.getDescription());
        }

        @Test
        @DisplayName("description 为空字符串时插入与查询应正确")
        void emptyDescriptionShouldBeStoredCorrectly() {
            testEnv.setDescription("");
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals("", saved.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为最小值时插入与查询应正确")
        void minSortOrderShouldBeStoredCorrectly() {
            testEnv.setSortOrder(Integer.MIN_VALUE);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals(Integer.MIN_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时插入与查询应正确")
        void maxSortOrderShouldBeStoredCorrectly() {
            testEnv.setSortOrder(Integer.MAX_VALUE);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals(Integer.MAX_VALUE, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为 null 时插入应使用数据库默认值 0")
        void nullSortOrderShouldBeStoredCorrectly() {
            testEnv.setSortOrder(null);
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            // 数据库列有 NOT NULL DEFAULT 0，null 被转换为 0
            assertEquals(0, saved.getSortOrder());
        }

        @Test
        @DisplayName("projectId 为 null 时插入与查询应正确")
        void nullProjectIdShouldBeStoredCorrectly() {
            testEnv.setProjectId(null);
            assertThrows(Exception.class, () ->
                    environmentMapper.insert(testEnv));
        }

        @Test
        @DisplayName("name 为 null 时插入应抛出约束异常")
        void nullNameShouldBeStoredCorrectly() {
            testEnv.setName(null);
            assertThrows(Exception.class, () ->
                    environmentMapper.insert(testEnv));
        }

        @Test
        @DisplayName("name 为空字符串时插入与查询应正确")
        void emptyNameShouldBeStoredCorrectly() {
            testEnv.setName("");
            environmentMapper.insert(testEnv);

            EnvironmentEntity saved = environmentMapper.selectById(testEnv.getId());
            assertEquals("", saved.getName());
        }
    }
}