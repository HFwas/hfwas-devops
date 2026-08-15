package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupUpdateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiGroupEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiGroupMapper;
import com.hfwas.devops.apitest.apidefine.service.ApiDefinitionService;
import com.hfwas.devops.apitest.apidefine.service.ApiGroupService;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiGroupService 业务逻辑测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖分组 CRUD、树形结构构建、业务校验（同名、存在子分组/接口约束）等核心逻辑。
 *
 * @author hfwas
 */
@DisplayName("ApiGroupService — 接口分组业务测试")
class ApiGroupServiceTest extends BaseApiTest {

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiGroupMapper apiGroupMapper;

    @Autowired
    private ApiDefinitionService apiDefinitionService;

    private ApiGroupCreateDTO rootCreateDTO;
    private ApiGroupCreateDTO childCreateDTO;

    @BeforeEach
    void setUp() {
        rootCreateDTO = new ApiGroupCreateDTO();
        rootCreateDTO.setProjectId(testProjectId());
        rootCreateDTO.setName("用户管理");
        rootCreateDTO.setSortOrder(1);
        rootCreateDTO.setDescription("用户相关接口分组");

        childCreateDTO = new ApiGroupCreateDTO();
        childCreateDTO.setProjectId(testProjectId());
        childCreateDTO.setName("登录注册");
        childCreateDTO.setSortOrder(1);
        childCreateDTO.setDescription("登录与注册接口");
    }

    // ========================================================================
    // create 创建分组
    // ========================================================================

    @Nested
    @DisplayName("create 创建分组")
    class CreateGroup {

        @Test
        @DisplayName("创建根级分组应成功并返回完整 VO")
        void createRootGroupShouldSucceed() {
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());

            assertNotNull(vo);
            assertNotNull(vo.getId());
            assertEquals("用户管理", vo.getName());
            assertEquals(testProjectId(), vo.getProjectId());
            assertNull(vo.getParentId());
            assertEquals(1, vo.getSortOrder());
            assertEquals("用户相关接口分组", vo.getDescription());
            assertNotNull(vo.getCreateTime());
            assertEquals(testUserId(), vo.getCreatedBy());
        }

        @Test
        @DisplayName("创建子级分组应正确关联父级 ID")
        void createChildGroupShouldLinkParent() {
            ApiGroupVO parent = apiGroupService.create(rootCreateDTO, testUserId());
            assertNotNull(parent.getId());

            childCreateDTO.setParentId(parent.getId());
            ApiGroupVO child = apiGroupService.create(childCreateDTO, testUserId());

            assertNotNull(child.getId());
            assertEquals(parent.getId(), child.getParentId());
            assertEquals("登录注册", child.getName());
        }

        @Test
        @DisplayName("同级同名分组创建应抛出 ApiTestException")
        void duplicateNameAtSameLevelShouldThrow() {
            apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupCreateDTO duplicate = new ApiGroupCreateDTO();
            duplicate.setProjectId(testProjectId());
            duplicate.setName("用户管理");
            duplicate.setSortOrder(2);

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.create(duplicate, testUserId()));
            assertTrue(ex.getMessage().contains("同名分组"));
        }

        @Test
        @DisplayName("不同父级下允许同名分组")
        void sameNameUnderDifferentParentsShouldBeAllowed() {
            ApiGroupVO parent1 = apiGroupService.create(rootCreateDTO, testUserId());

            // 创建另一个根级分组
            ApiGroupCreateDTO anotherRoot = new ApiGroupCreateDTO();
            anotherRoot.setProjectId(testProjectId());
            anotherRoot.setName("订单管理");
            anotherRoot.setSortOrder(2);
            ApiGroupVO parent2 = apiGroupService.create(anotherRoot, testUserId());

            // 在两个不同父级下创建同名子分组
            childCreateDTO.setParentId(parent1.getId());
            childCreateDTO.setName("列表查询");
            apiGroupService.create(childCreateDTO, testUserId());

            childCreateDTO.setParentId(parent2.getId());
            childCreateDTO.setName("列表查询");
            apiGroupService.create(childCreateDTO, testUserId());

            // 验证两个同名分组都存在
            List<ApiGroupEntity> all = apiGroupMapper.selectList(null);
            long count = all.stream().filter(g -> "列表查询".equals(g.getName())).count();
            assertEquals(2, count);
        }

        @Test
        @DisplayName("不同项目下允许同名分组")
        void sameNameInDifferentProjectsShouldBeAllowed() {
            apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupCreateDTO otherProject = new ApiGroupCreateDTO();
            otherProject.setProjectId(2002L);
            otherProject.setName("用户管理");
            otherProject.setSortOrder(1);
            ApiGroupVO vo = apiGroupService.create(otherProject, testUserId());

            assertNotNull(vo.getId());
        }

        @Test
        @DisplayName("sortOrder 为空时应自动填充默认值 0")
        void nullSortOrderShouldDefaultToZero() {
            rootCreateDTO.setSortOrder(null);
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());

            assertEquals(0, vo.getSortOrder());
        }

        @Test
        @DisplayName("创建后数据库应保存正确的记录")
        void createShouldPersistToDatabase() {
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupEntity saved = apiGroupMapper.selectById(vo.getId());
            assertNotNull(saved);
            assertEquals("用户管理", saved.getName());
            assertEquals(testProjectId(), saved.getProjectId());
            assertEquals(1, saved.getSortOrder());
            assertEquals("用户相关接口分组", saved.getDescription());
            assertEquals(0, saved.getDeleted().intValue());
        }

        @Test
        @DisplayName("创建后审计字段应自动填充")
        void createShouldAutoFillAuditFields() {
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupEntity saved = apiGroupMapper.selectById(vo.getId());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
        }
    }

    // ========================================================================
    // update 更新分组
    // ========================================================================

    @Nested
    @DisplayName("update 更新分组")
    class UpdateGroup {

        private Long groupId;

        @BeforeEach
        void insertGroup() {
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());
            groupId = vo.getId();
        }

        private ApiGroupUpdateDTO buildUpdateDTO(String name, Integer sortOrder, String description) {
            ApiGroupUpdateDTO dto = new ApiGroupUpdateDTO();
            dto.setName(name);
            dto.setSortOrder(sortOrder);
            dto.setDescription(description);
            return dto;
        }

        @Test
        @DisplayName("更新分组名称应成功")
        void updateNameShouldSucceed() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理V2", 1, "用户相关接口分组");
            ApiGroupVO vo = apiGroupService.update(groupId, dto, testUserId());

            assertEquals("用户管理V2", vo.getName());

            ApiGroupEntity saved = apiGroupMapper.selectById(groupId);
            assertEquals("用户管理V2", saved.getName());
        }

        @Test
        @DisplayName("更新分组描述应成功")
        void updateDescriptionShouldSucceed() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理", 1, "更新后的描述");
            ApiGroupVO vo = apiGroupService.update(groupId, dto, testUserId());

            assertEquals("更新后的描述", vo.getDescription());

            ApiGroupEntity saved = apiGroupMapper.selectById(groupId);
            assertEquals("更新后的描述", saved.getDescription());
        }

        @Test
        @DisplayName("更新分组排序应成功")
        void updateSortOrderShouldSucceed() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理", 99, "用户相关接口分组");
            ApiGroupVO vo = apiGroupService.update(groupId, dto, testUserId());

            assertEquals(99, vo.getSortOrder());

            ApiGroupEntity saved = apiGroupMapper.selectById(groupId);
            assertEquals(99, saved.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为空时应保持原有值不变")
        void nullSortOrderShouldKeepOriginal() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理", null, "用户相关接口分组");
            ApiGroupVO vo = apiGroupService.update(groupId, dto, testUserId());

            assertEquals(1, vo.getSortOrder());
        }

        @Test
        @DisplayName("更新为同级同名分组应抛出 ApiTestException")
        void updateToDuplicateNameShouldThrow() {
            // 创建另一个分组
            ApiGroupCreateDTO another = new ApiGroupCreateDTO();
            another.setProjectId(testProjectId());
            another.setName("订单管理");
            another.setSortOrder(2);
            apiGroupService.create(another, testUserId());

            // 尝试将第一个分组改名为"订单管理"
            ApiGroupUpdateDTO dto = buildUpdateDTO("订单管理", 1, null);
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.update(groupId, dto, testUserId()));
            assertTrue(ex.getMessage().contains("同名分组"));
        }

        @Test
        @DisplayName("更新自身名称为原名不应抛出异常")
        void updateToSameNameShouldSucceed() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理", 2, "更新描述");
            ApiGroupVO vo = apiGroupService.update(groupId, dto, testUserId());

            assertEquals("用户管理", vo.getName());
            assertEquals(2, vo.getSortOrder());
        }

        @Test
        @DisplayName("更新不存在的分组应抛出 ApiTestException")
        void updateNonExistentGroupShouldThrow() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("不存在", 1, null);
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.update(99999L, dto, testUserId()));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("更新后 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillAuditFields() {
            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理V2", 1, null);
            apiGroupService.update(groupId, dto, testUserId());

            ApiGroupEntity saved = apiGroupMapper.selectById(groupId);
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getUpdateBy());
        }
    }

    // ========================================================================
    // delete 删除分组
    // ========================================================================

    @Nested
    @DisplayName("delete 删除分组")
    class DeleteGroup {

        private Long rootId;

        @BeforeEach
        void insertRootGroup() {
            ApiGroupVO vo = apiGroupService.create(rootCreateDTO, testUserId());
            rootId = vo.getId();
        }

        @Test
        @DisplayName("删除无子分组和接口的分组应成功")
        void deleteGroupWithoutChildrenOrApisShouldSucceed() {
            assertDoesNotThrow(() -> apiGroupService.delete(rootId));

            ApiGroupEntity deleted = apiGroupMapper.selectById(rootId);
            assertNull(deleted);
        }

        @Test
        @DisplayName("删除存在子分组的分组应抛出 ApiTestException")
        void deleteGroupWithChildrenShouldThrow() {
            childCreateDTO.setParentId(rootId);
            apiGroupService.create(childCreateDTO, testUserId());

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.delete(rootId));
            assertTrue(ex.getMessage().contains("先删除子分组"));
        }

        @Test
        @DisplayName("删除存在接口的分组应抛出 ApiTestException")
        void deleteGroupWithApisShouldThrow() {
            // 插入一条接口定义，关联到该分组
            ApiDefinitionEntity apiDef = new ApiDefinitionEntity();
            apiDef.setProjectId(testProjectId());
            apiDef.setGroupId(rootId);
            apiDef.setName("获取用户列表");
            apiDef.setPath("/api/users");
            apiDef.setMethod("GET");
            apiDef.setStatus("DRAFT");
            apiDefinitionService.save(apiDef);

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.delete(rootId));
            assertTrue(ex.getMessage().contains("存在接口"));
        }

        @Test
        @DisplayName("删除不存在的分组应抛出 ApiTestException")
        void deleteNonExistentGroupShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.delete(99999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("删除后应记录日志（不抛异常即为成功）")
        void deleteShouldLogSuccess() {
            // 先删除无依赖的子分组
            childCreateDTO.setParentId(rootId);
            childCreateDTO.setName("可删除子分组");
            ApiGroupVO child = apiGroupService.create(childCreateDTO, testUserId());

            // 删除子分组应成功
            assertDoesNotThrow(() -> apiGroupService.delete(child.getId()));

            // 现在根分组没有子分组了，可以删除
            assertDoesNotThrow(() -> apiGroupService.delete(rootId));
        }
    }

    // ========================================================================
    // getGroupTree 获取分组树
    // ========================================================================

    @Nested
    @DisplayName("getGroupTree 获取分组树")
    class GetGroupTree {

        @Test
        @DisplayName("项目下无分组时应返回空列表")
        void noGroupsShouldReturnEmptyList() {
            List<ApiGroupVO> tree = apiGroupService.getGroupTree(testProjectId());
            assertNotNull(tree);
            assertTrue(tree.isEmpty());
        }

        @Test
        @DisplayName("仅有根级分组时应返回扁平列表")
        void onlyRootGroupsShouldReturnFlatList() {
            apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupCreateDTO another = new ApiGroupCreateDTO();
            another.setProjectId(testProjectId());
            another.setName("订单管理");
            another.setSortOrder(2);
            apiGroupService.create(another, testUserId());

            List<ApiGroupVO> tree = apiGroupService.getGroupTree(testProjectId());
            assertEquals(2, tree.size());
            assertTrue(tree.stream().allMatch(g -> g.getChildren() == null || g.getChildren().isEmpty()));
        }

        @Test
        @DisplayName("多级分组应返回正确树形结构")
        void multiLevelGroupsShouldReturnTreeStructure() {
            // 创建根分组
            ApiGroupVO root = apiGroupService.create(rootCreateDTO, testUserId());

            // 创建子分组
            childCreateDTO.setParentId(root.getId());
            ApiGroupVO child = apiGroupService.create(childCreateDTO, testUserId());

            // 创建孙分组
            ApiGroupCreateDTO grandChild = new ApiGroupCreateDTO();
            grandChild.setProjectId(testProjectId());
            grandChild.setParentId(child.getId());
            grandChild.setName("手机号登录");
            grandChild.setSortOrder(1);
            apiGroupService.create(grandChild, testUserId());

            // 获取树
            List<ApiGroupVO> tree = apiGroupService.getGroupTree(testProjectId());
            assertEquals(1, tree.size());

            ApiGroupVO rootVO = tree.get(0);
            assertEquals("用户管理", rootVO.getName());
            assertNotNull(rootVO.getChildren());
            assertEquals(1, rootVO.getChildren().size());

            ApiGroupVO childVO = rootVO.getChildren().get(0);
            assertEquals("登录注册", childVO.getName());
            assertNotNull(childVO.getChildren());
            assertEquals(1, childVO.getChildren().size());
            assertEquals("手机号登录", childVO.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("分组树应按 sortOrder 升序排列")
        void treeShouldBeOrderedBySortOrder() {
            ApiGroupCreateDTO g1 = new ApiGroupCreateDTO();
            g1.setProjectId(testProjectId());
            g1.setName("分组A");
            g1.setSortOrder(3);
            apiGroupService.create(g1, testUserId());

            ApiGroupCreateDTO g2 = new ApiGroupCreateDTO();
            g2.setProjectId(testProjectId());
            g2.setName("分组B");
            g2.setSortOrder(1);
            apiGroupService.create(g2, testUserId());

            ApiGroupCreateDTO g3 = new ApiGroupCreateDTO();
            g3.setProjectId(testProjectId());
            g3.setName("分组C");
            g3.setSortOrder(2);
            apiGroupService.create(g3, testUserId());

            List<ApiGroupVO> tree = apiGroupService.getGroupTree(testProjectId());
            assertEquals(3, tree.size());
            assertEquals("分组B", tree.get(0).getName());
            assertEquals("分组C", tree.get(1).getName());
            assertEquals("分组A", tree.get(2).getName());
        }

        @Test
        @DisplayName("不同项目应返回各自的树，互不干扰")
        void differentProjectsShouldReturnIndependentTrees() {
            apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupCreateDTO other = new ApiGroupCreateDTO();
            other.setProjectId(2002L);
            other.setName("其他项目分组");
            other.setSortOrder(1);
            apiGroupService.create(other, testUserId());

            List<ApiGroupVO> tree1 = apiGroupService.getGroupTree(testProjectId());
            assertEquals(1, tree1.size());
            assertEquals("用户管理", tree1.get(0).getName());

            List<ApiGroupVO> tree2 = apiGroupService.getGroupTree(2002L);
            assertEquals(1, tree2.size());
            assertEquals("其他项目分组", tree2.get(0).getName());
        }

        @Test
        @DisplayName("分组树中应包含 apiCount 统计")
        void treeShouldIncludeApiCount() {
            ApiGroupVO root = apiGroupService.create(rootCreateDTO, testUserId());

            // 插入接口定义关联到该分组
            ApiDefinitionEntity apiDef1 = new ApiDefinitionEntity();
            apiDef1.setProjectId(testProjectId());
            apiDef1.setGroupId(root.getId());
            apiDef1.setName("获取用户列表");
            apiDef1.setPath("/api/users");
            apiDef1.setMethod("GET");
            apiDef1.setStatus("DRAFT");
            apiDefinitionService.save(apiDef1);

            ApiDefinitionEntity apiDef2 = new ApiDefinitionEntity();
            apiDef2.setProjectId(testProjectId());
            apiDef2.setGroupId(root.getId());
            apiDef2.setName("创建用户");
            apiDef2.setPath("/api/users");
            apiDef2.setMethod("POST");
            apiDef2.setStatus("DRAFT");
            apiDefinitionService.save(apiDef2);

            // 创建子分组，不关联接口
            childCreateDTO.setParentId(root.getId());
            apiGroupService.create(childCreateDTO, testUserId());

            List<ApiGroupVO> tree = apiGroupService.getGroupTree(testProjectId());
            assertEquals(1, tree.size());
            assertEquals(2, tree.get(0).getApiCount().intValue());
            assertEquals(0, tree.get(0).getChildren().get(0).getApiCount().intValue());
        }
    }

    // ========================================================================
    // getDetail 获取分组详情
    // ========================================================================

    @Nested
    @DisplayName("getDetail 获取分组详情")
    class GetDetail {

        @Test
        @DisplayName("查询存在的分组应返回完整 VO")
        void getExistingGroupShouldReturnDetail() {
            ApiGroupVO created = apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupVO detail = apiGroupService.getDetail(created.getId());
            assertNotNull(detail);
            assertEquals(created.getId(), detail.getId());
            assertEquals("用户管理", detail.getName());
            assertEquals(testProjectId(), detail.getProjectId());
            assertEquals(1, detail.getSortOrder());
            assertEquals("用户相关接口分组", detail.getDescription());
            assertNotNull(detail.getCreateTime());
            assertEquals(testUserId(), detail.getCreatedBy());
        }

        @Test
        @DisplayName("查询不存在的分组应抛出 ApiTestException")
        void getNonExistentGroupShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiGroupService.getDetail(99999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("查询子分组详情应正确返回父级 ID")
        void getChildGroupDetailShouldReturnParentId() {
            ApiGroupVO parent = apiGroupService.create(rootCreateDTO, testUserId());
            childCreateDTO.setParentId(parent.getId());
            ApiGroupVO child = apiGroupService.create(childCreateDTO, testUserId());

            ApiGroupVO detail = apiGroupService.getDetail(child.getId());
            assertEquals(parent.getId(), detail.getParentId());
        }

        @Test
        @DisplayName("多次查询同一分组应返回一致结果")
        void multipleQueriesShouldReturnConsistentResult() {
            ApiGroupVO created = apiGroupService.create(rootCreateDTO, testUserId());

            ApiGroupVO detail1 = apiGroupService.getDetail(created.getId());
            ApiGroupVO detail2 = apiGroupService.getDetail(created.getId());

            assertEquals(detail1.getId(), detail2.getId());
            assertEquals(detail1.getName(), detail2.getName());
            assertEquals(detail1.getSortOrder(), detail2.getSortOrder());
        }
    }
}