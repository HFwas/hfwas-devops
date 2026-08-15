package com.hfwas.devops.apitest.collection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.collection.dto.CollectionCreateDTO;
import com.hfwas.devops.apitest.collection.dto.CollectionUpdateDTO;
import com.hfwas.devops.apitest.collection.entity.CollectionEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionFolderEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionItemEntity;
import com.hfwas.devops.apitest.collection.entity.CollectionRunEntity;
import com.hfwas.devops.apitest.collection.mapper.CollectionFolderMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionItemMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionMapper;
import com.hfwas.devops.apitest.collection.mapper.CollectionRunMapper;
import com.hfwas.devops.apitest.collection.service.CollectionFolderService;
import com.hfwas.devops.apitest.collection.service.CollectionItemService;
import com.hfwas.devops.apitest.collection.service.CollectionService;
import com.hfwas.devops.apitest.collection.vo.CollectionDetailVO;
import com.hfwas.devops.apitest.collection.vo.CollectionFolderVO;
import com.hfwas.devops.apitest.collection.vo.CollectionItemVO;
import com.hfwas.devops.apitest.collection.vo.CollectionVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollectionService 业务逻辑测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖集合 CRUD、分页查询、文件夹树形结构、名称唯一性校验、级联删除等核心逻辑。
 *
 * @author hfwas
 */
@DisplayName("CollectionService — 集合业务测试")
class CollectionServiceTest extends BaseApiTest {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private CollectionMapper collectionMapper;

    @Autowired
    private CollectionFolderMapper folderMapper;

    @Autowired
    private CollectionItemMapper itemMapper;

    @Autowired
    private CollectionRunMapper runMapper;

    @Autowired
    private ApiDefinitionMapper definitionMapper;

    @Autowired
    private CollectionFolderService folderService;

    @Autowired
    private CollectionItemService itemService;

    private CollectionCreateDTO createDTO;

    @BeforeEach
    void setUp() {
        createDTO = new CollectionCreateDTO();
        createDTO.setName("用户管理接口集合");
        createDTO.setDescription("包含用户管理模块的所有接口");
        createDTO.setSortOrder(1);
    }

    // ========================================================================
    // create 创建集合
    // ========================================================================

    @Nested
    @DisplayName("create 创建集合")
    class CreateCollection {

        @Test
        @DisplayName("创建基本集合应成功并返回完整 VO")
        void createBasicCollectionShouldSucceed() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());

            assertNotNull(vo);
            assertNotNull(vo.getId());
            assertEquals("用户管理接口集合", vo.getName());
            assertEquals("包含用户管理模块的所有接口", vo.getDescription());
            assertEquals(testProjectId(), vo.getProjectId());
            assertEquals(1, vo.getSortOrder());
            assertEquals(0, vo.getFolderCount());
            assertEquals(0, vo.getItemCount());
            assertNotNull(vo.getCreateTime());
            assertNotNull(vo.getUpdateTime());
        }

        @Test
        @DisplayName("创建后数据库应保存正确的记录")
        void createShouldPersistToDatabase() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());

            CollectionEntity saved = collectionMapper.selectById(vo.getId());
            assertNotNull(saved);
            assertEquals("用户管理接口集合", saved.getName());
            assertEquals("包含用户管理模块的所有接口", saved.getDescription());
            assertEquals(testProjectId(), saved.getProjectId());
            assertEquals(1, saved.getSortOrder());
            assertEquals(0, saved.getDeleted().intValue());
        }

        @Test
        @DisplayName("创建后审计字段应自动填充")
        void createShouldAutoFillAuditFields() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());

            CollectionEntity saved = collectionMapper.selectById(vo.getId());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
        }

        @Test
        @DisplayName("description 为 null 时创建应成功")
        void createWithNullDescriptionShouldSucceed() {
            createDTO.setDescription(null);

            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            assertNotNull(vo.getId());
            assertNull(vo.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为 null 时创建应使用默认值 0")
        void createWithNullSortOrderShouldUseDefault() {
            createDTO.setSortOrder(null);

            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            assertEquals(0, vo.getSortOrder());

            CollectionEntity saved = collectionMapper.selectById(vo.getId());
            assertEquals(0, saved.getSortOrder());
        }

        @Test
        @DisplayName("同一项目下同名集合创建应抛出 ApiTestException")
        void duplicateNameInSameProjectShouldThrow() {
            collectionService.create(createDTO, testProjectId(), testUserId());

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.create(createDTO, testProjectId(), testUserId()));
            assertTrue(ex.getMessage().contains("同名集合"));
        }

        @Test
        @DisplayName("不同项目下同名集合应允许创建")
        void sameNameInDifferentProjectsShouldBeAllowed() {
            collectionService.create(createDTO, testProjectId(), testUserId());

            CollectionVO vo = collectionService.create(createDTO, 2002L, testUserId());
            assertNotNull(vo.getId());
            assertEquals("用户管理接口集合", vo.getName());
        }

        @Test
        @DisplayName("description 为空字符串时创建应成功")
        void createWithEmptyDescriptionShouldSucceed() {
            createDTO.setDescription("");

            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            assertEquals("", vo.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为负数时创建应成功")
        void createWithNegativeSortOrderShouldSucceed() {
            createDTO.setSortOrder(-1);

            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            assertEquals(-1, vo.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时创建应成功")
        void createWithMaxSortOrderShouldSucceed() {
            createDTO.setSortOrder(Integer.MAX_VALUE);

            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            assertEquals(Integer.MAX_VALUE, vo.getSortOrder());
        }
    }

    // ========================================================================
    // update 更新集合
    // ========================================================================

    @Nested
    @DisplayName("update 更新集合")
    class UpdateCollection {

        private Long collectionId;

        @BeforeEach
        void insertCollection() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            collectionId = vo.getId();
        }

        private CollectionUpdateDTO buildUpdateDTO(String name, String description, Integer sortOrder) {
            CollectionUpdateDTO dto = new CollectionUpdateDTO();
            dto.setName(name);
            dto.setDescription(description);
            dto.setSortOrder(sortOrder);
            return dto;
        }

        @Test
        @DisplayName("更新集合名称应成功")
        void updateNameShouldSucceed() {
            CollectionUpdateDTO dto = buildUpdateDTO("用户管理接口集合V2", null, null);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());

            assertEquals("用户管理接口集合V2", vo.getName());

            CollectionEntity saved = collectionMapper.selectById(collectionId);
            assertEquals("用户管理接口集合V2", saved.getName());
        }

        @Test
        @DisplayName("更新集合描述应成功")
        void updateDescriptionShouldSucceed() {
            CollectionUpdateDTO dto = buildUpdateDTO(null, "更新后的描述信息", null);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());

            assertEquals("更新后的描述信息", vo.getDescription());

            CollectionEntity saved = collectionMapper.selectById(collectionId);
            assertEquals("更新后的描述信息", saved.getDescription());
        }

        @Test
        @DisplayName("更新集合排序序号应成功")
        void updateSortOrderShouldSucceed() {
            CollectionUpdateDTO dto = buildUpdateDTO(null, null, 99);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());

            assertEquals(99, vo.getSortOrder());

            CollectionEntity saved = collectionMapper.selectById(collectionId);
            assertEquals(99, saved.getSortOrder());
        }

        @Test
        @DisplayName("同时更新多个字段应全部生效")
        void updateMultipleFieldsShouldSucceed() {
            CollectionUpdateDTO dto = buildUpdateDTO("新名称", "新描述", 50);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());

            assertEquals("新名称", vo.getName());
            assertEquals("新描述", vo.getDescription());
            assertEquals(50, vo.getSortOrder());
        }

        @Test
        @DisplayName("更新后 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillAuditFields() {
            CollectionUpdateDTO dto = buildUpdateDTO("新名称", null, null);
            collectionService.update(collectionId, dto, testUserId());

            CollectionEntity saved = collectionMapper.selectById(collectionId);
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getUpdateBy());
        }

        @Test
        @DisplayName("更新为同一项目下的同名集合应抛出 ApiTestException")
        void updateToDuplicateNameShouldThrow() {
            // 创建另一个集合
            CollectionCreateDTO another = new CollectionCreateDTO();
            another.setName("订单管理接口集合");
            another.setSortOrder(2);
            collectionService.create(another, testProjectId(), testUserId());

            // 尝试将第一个集合的名称改为和第二个相同
            CollectionUpdateDTO dto = buildUpdateDTO("订单管理接口集合", null, null);
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.update(collectionId, dto, testUserId()));
            assertTrue(ex.getMessage().contains("同名集合"));
        }

        @Test
        @DisplayName("更新为自身原名应不抛出异常")
        void updateToSameNameShouldSucceed() {
            CollectionUpdateDTO dto = buildUpdateDTO("用户管理接口集合", "更新描述", null);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());
            assertEquals("用户管理接口集合", vo.getName());
            assertEquals("更新描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新不存在的集合应抛出 ApiTestException")
        void updateNonExistentCollectionShouldThrow() {
            CollectionUpdateDTO dto = buildUpdateDTO("不存在", null, null);
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.update(99999L, dto, testUserId()));
            assertTrue(ex.getMessage().contains("集合不存在"));
        }

        @Test
        @DisplayName("更新 description 为 null 应保持原值（服务层仅更新非 null 字段）")
        void updateDescriptionToNullShouldSucceed() {
            // 先确认原有描述不为空
            CollectionEntity before = collectionMapper.selectById(collectionId);
            assertNotNull(before.getDescription());

            CollectionUpdateDTO dto = new CollectionUpdateDTO();
            dto.setDescription(null);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());
            // 服务层仅更新非 null 字段，description 为 null 时不更新
            assertNotNull(vo.getDescription());
        }

        @Test
        @DisplayName("更新 description 为空字符串应成功")
        void updateDescriptionToEmptyShouldSucceed() {
            CollectionUpdateDTO dto = new CollectionUpdateDTO();
            dto.setDescription("");
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());
            assertEquals("", vo.getDescription());
        }

        @Test
        @DisplayName("不同项目下同名集合更新应允许")
        void updateWithSameNameAcrossProjectsShouldBeAllowed() {
            // 在另一个项目下创建同名集合
            collectionService.create(createDTO, 2002L, testUserId());

            // 更新第一个集合的名称，不应受另一个项目影响
            CollectionUpdateDTO dto = buildUpdateDTO("用户管理接口集合", null, null);
            CollectionVO vo = collectionService.update(collectionId, dto, testUserId());
            assertEquals("用户管理接口集合", vo.getName());
        }
    }

    // ========================================================================
    // getDetail 获取集合详情
    // ========================================================================

    @Nested
    @DisplayName("getDetail 获取集合详情")
    class GetDetail {

        private Long collectionId;

        @BeforeEach
        void insertCollection() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            collectionId = vo.getId();
        }

        @Test
        @DisplayName("查询存在的集合应返回基本信息")
        void getExistingCollectionShouldReturnBasicInfo() {
            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertNotNull(detail);
            assertEquals(collectionId, detail.getId());
            assertEquals("用户管理接口集合", detail.getName());
            assertEquals("包含用户管理模块的所有接口", detail.getDescription());
            assertEquals(testProjectId(), detail.getProjectId());
            assertEquals(1, detail.getSortOrder());
        }

        @Test
        @DisplayName("无文件夹和项的集合应返回空列表")
        void getDetailWithoutFoldersAndItems() {
            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertNotNull(detail.getFolders());
            assertTrue(detail.getFolders().isEmpty());
            assertNotNull(detail.getItems());
            assertTrue(detail.getItems().isEmpty());
        }

        @Test
        @DisplayName("查询不存在的集合应抛出 ApiTestException")
        void getNonExistentCollectionShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.getDetail(99999L));
            assertTrue(ex.getMessage().contains("集合不存在"));
        }

        @Test
        @DisplayName("含根级文件夹的集合详情应返回文件夹树")
        void getDetailWithRootFolders() {
            // 插入一个根级文件夹
            CollectionFolderEntity folder = new CollectionFolderEntity();
            folder.setCollectionId(collectionId);
            folder.setName("用户管理");
            folder.setSortOrder(1);
            folderMapper.insert(folder);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertEquals(1, detail.getFolders().size());
            assertEquals("用户管理", detail.getFolders().get(0).getName());
            assertTrue(detail.getFolders().get(0).getChildren().isEmpty());
            assertTrue(detail.getFolders().get(0).getItems().isEmpty());
            assertTrue(detail.getItems().isEmpty());
        }

        @Test
        @DisplayName("含嵌套文件夹的集合详情应返回完整树形结构")
        void getDetailWithNestedFolders() {
            // 根级文件夹
            CollectionFolderEntity parent = new CollectionFolderEntity();
            parent.setCollectionId(collectionId);
            parent.setName("用户管理");
            parent.setSortOrder(1);
            folderMapper.insert(parent);

            // 子文件夹
            CollectionFolderEntity child = new CollectionFolderEntity();
            child.setCollectionId(collectionId);
            child.setParentId(parent.getId());
            child.setName("权限管理");
            child.setSortOrder(1);
            folderMapper.insert(child);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertEquals(1, detail.getFolders().size());
            CollectionFolderVO parentVO = detail.getFolders().get(0);
            assertEquals("用户管理", parentVO.getName());
            assertEquals(1, parentVO.getChildren().size());
            assertEquals("权限管理", parentVO.getChildren().get(0).getName());
        }

        @Test
        @DisplayName("含根级集合项的详情应返回未归入文件夹的项")
        void getDetailWithRootItems() {
            // 插入一条接口定义用于引用
            ApiDefinitionEntity def = insertApiDefinition();

            // 插入根级集合项
            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(collectionId);
            item.setDefinitionId(def.getId());
            item.setName("获取用户列表");
            item.setEnabled(1);
            item.setSortOrder(1);
            itemMapper.insert(item);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertEquals(1, detail.getItems().size());
            assertEquals("获取用户列表", detail.getItems().get(0).getName());
            assertTrue(detail.getItems().get(0).getEnabled());
            assertEquals(def.getMethod(), detail.getItems().get(0).getMethod());
            assertEquals(def.getPath(), detail.getItems().get(0).getPath());
        }

        @Test
        @DisplayName("含文件夹内集合项的详情应返回文件夹下项")
        void getDetailWithFolderItems() {
            // 插入接口定义
            ApiDefinitionEntity def = insertApiDefinition();

            // 插入文件夹
            CollectionFolderEntity folder = new CollectionFolderEntity();
            folder.setCollectionId(collectionId);
            folder.setName("用户管理");
            folder.setSortOrder(1);
            folderMapper.insert(folder);

            // 插入文件夹内的集合项
            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(collectionId);
            item.setFolderId(folder.getId());
            item.setDefinitionId(def.getId());
            item.setName("获取用户列表");
            item.setEnabled(1);
            item.setSortOrder(1);
            itemMapper.insert(item);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            assertEquals(1, detail.getFolders().size());
            assertEquals(1, detail.getFolders().get(0).getItems().size());
            assertEquals("获取用户列表", detail.getFolders().get(0).getItems().get(0).getName());
            assertTrue(detail.getItems().isEmpty());
        }

        @Test
        @DisplayName("文件夹和集合项应分别按 sortOrder 排序")
        void foldersAndItemsShouldBeSortedBySortOrder() {
            // 插入多个绑定接口定义
            ApiDefinitionEntity def1 = insertApiDefinition("GET", "/api/users");
            ApiDefinitionEntity def2 = insertApiDefinition("POST", "/api/users");
            ApiDefinitionEntity def3 = insertApiDefinition("PUT", "/api/users/{id}");

            // 插入文件夹
            CollectionFolderEntity folder1 = new CollectionFolderEntity();
            folder1.setCollectionId(collectionId);
            folder1.setName("B文件夹");
            folder1.setSortOrder(2);
            folderMapper.insert(folder1);

            CollectionFolderEntity folder2 = new CollectionFolderEntity();
            folder2.setCollectionId(collectionId);
            folder2.setName("A文件夹");
            folder2.setSortOrder(1);
            folderMapper.insert(folder2);

            // 插入集合项
            CollectionItemEntity item1 = new CollectionItemEntity();
            item1.setCollectionId(collectionId);
            item1.setFolderId(folder2.getId());
            item1.setDefinitionId(def3.getId());
            item1.setName("C项");
            item1.setEnabled(1);
            item1.setSortOrder(3);
            itemMapper.insert(item1);

            CollectionItemEntity item2 = new CollectionItemEntity();
            item2.setCollectionId(collectionId);
            item2.setFolderId(folder2.getId());
            item2.setDefinitionId(def2.getId());
            item2.setName("A项");
            item2.setEnabled(1);
            item2.setSortOrder(1);
            itemMapper.insert(item2);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);

            // 文件夹按 sortOrder 升序
            assertEquals("A文件夹", detail.getFolders().get(0).getName());
            assertEquals("B文件夹", detail.getFolders().get(1).getName());

            // 文件夹内项按 sortOrder 升序
            assertEquals("A项", detail.getFolders().get(0).getItems().get(0).getName());
            assertEquals("C项", detail.getFolders().get(0).getItems().get(1).getName());
        }

        @Test
        @DisplayName("集合项引用不存在的接口定义时不应报错")
        void itemWithMissingDefinitionShouldNotThrow() {
            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(collectionId);
            item.setDefinitionId(99999L);
            item.setName("孤立项");
            item.setEnabled(1);
            item.setSortOrder(1);
            itemMapper.insert(item);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);
            assertEquals(1, detail.getItems().size());
            assertEquals("孤立项", detail.getItems().get(0).getName());
            assertNull(detail.getItems().get(0).getMethod());
            assertNull(detail.getItems().get(0).getPath());
        }

        @Test
        @DisplayName("禁用的集合项应正确反映 enabled 为 false")
        void disabledItemShouldReflectEnabledFalse() {
            ApiDefinitionEntity def = insertApiDefinition();

            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(collectionId);
            item.setDefinitionId(def.getId());
            item.setName("已禁用项");
            item.setEnabled(0);
            item.setSortOrder(1);
            itemMapper.insert(item);

            CollectionDetailVO detail = collectionService.getDetail(collectionId);
            assertFalse(detail.getItems().get(0).getEnabled());
        }
    }

    // ========================================================================
    // delete 删除集合
    // ========================================================================

    @Nested
    @DisplayName("delete 删除集合")
    class DeleteCollection {

        private Long collectionId;

        @BeforeEach
        void insertCollectionWithRelations() {
            CollectionVO vo = collectionService.create(createDTO, testProjectId(), testUserId());
            collectionId = vo.getId();

            // 插入文件夹
            CollectionFolderEntity folder = new CollectionFolderEntity();
            folder.setCollectionId(collectionId);
            folder.setName("用户管理");
            folder.setSortOrder(1);
            folderMapper.insert(folder);

            // 插入接口定义
            ApiDefinitionEntity def = insertApiDefinition();

            // 插入集合项
            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(collectionId);
            item.setFolderId(folder.getId());
            item.setDefinitionId(def.getId());
            item.setName("获取用户列表");
            item.setEnabled(1);
            item.setSortOrder(1);
            itemMapper.insert(item);

            // 插入执行记录
            CollectionRunEntity run = new CollectionRunEntity();
            run.setCollectionId(collectionId);
            run.setProjectId(testProjectId());
            run.setStatus("PENDING");
            runMapper.insert(run);
        }

        @Test
        @DisplayName("删除集合应级联删除关联的文件夹、项和执行记录")
        void deleteShouldCascadeToRelatedData() {
            // 确认删除前数据存在
            assertNotNull(collectionMapper.selectById(collectionId));
            assertFalse(folderMapper.selectList(null).isEmpty());
            assertFalse(itemMapper.selectList(null).isEmpty());
            assertFalse(runMapper.selectList(null).isEmpty());

            collectionService.delete(collectionId);

            // 主表被逻辑删除
            assertNull(collectionMapper.selectById(collectionId));

            // 关联数据被逻辑删除
            assertTrue(folderMapper.selectList(null).isEmpty());
            assertTrue(itemMapper.selectList(null).isEmpty());
            assertTrue(runMapper.selectList(null).isEmpty());
        }

        @Test
        @DisplayName("删除不存在的集合应抛出 ApiTestException")
        void deleteNonExistentCollectionShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.delete(99999L));
            assertTrue(ex.getMessage().contains("集合不存在"));
        }

        @Test
        @DisplayName("删除无关联数据的集合应成功")
        void deleteCollectionWithoutRelationsShouldSucceed() {
            // 创建无关联数据的集合
            CollectionVO emptyVo = collectionService.create(
                    new CollectionCreateDTO() {{
                        setName("空集合");
                        setSortOrder(2);
                    }},
                    testProjectId(), testUserId());

            collectionService.delete(emptyVo.getId());
            assertNull(collectionMapper.selectById(emptyVo.getId()));
        }

        @Test
        @DisplayName("多次删除同一集合应抛出 ApiTestException")
        void deleteAlreadyDeletedCollectionShouldThrow() {
            collectionService.delete(collectionId);

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> collectionService.delete(collectionId));
            assertTrue(ex.getMessage().contains("集合不存在"));
        }
    }

    // ========================================================================
    // pageQuery 分页查询集合列表
    // ========================================================================

    @Nested
    @DisplayName("pageQuery 分页查询集合列表")
    class PageQueryCollection {

        @BeforeEach
        void insertTestData() {
            collectionService.create(createDTO, testProjectId(), testUserId());

            CollectionCreateDTO orderApi = new CollectionCreateDTO();
            orderApi.setName("订单管理接口集合");
            orderApi.setDescription("包含订单管理模块的所有接口");
            orderApi.setSortOrder(2);
            collectionService.create(orderApi, testProjectId(), testUserId());

            CollectionCreateDTO productApi = new CollectionCreateDTO();
            productApi.setName("商品管理接口集合");
            productApi.setDescription("包含商品管理模块的所有接口");
            productApi.setSortOrder(3);
            collectionService.create(productApi, testProjectId(), testUserId());
        }

        @Test
        @DisplayName("分页查询所有集合应返回全部记录")
        void pageQueryAllShouldReturnAllCollections() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 1, 20);

            assertNotNull(result);
            assertEquals(3, result.getTotal());
            assertEquals(3, result.getRecords().size());
        }

        @Test
        @DisplayName("分页查询应按 sortOrder 升序排列")
        void pageQueryShouldBeOrderedBySortOrder() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 1, 20);

            List<CollectionVO> records = result.getRecords();
            for (int i = 1; i < records.size(); i++) {
                assertTrue(records.get(i - 1).getSortOrder() <= records.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("按关键词搜索名称应返回匹配结果")
        void filterByKeywordShouldReturnMatchingCollections() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), "用户", 1, 20);

            assertEquals(1, result.getTotal());
            assertEquals("用户管理接口集合", result.getRecords().get(0).getName());
        }

        @Test
        @DisplayName("按关键词搜索应返回模糊匹配结果")
        void filterByKeywordShouldReturnFuzzyMatch() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), "管理", 1, 20);

            assertEquals(3, result.getTotal());
        }

        @Test
        @DisplayName("无匹配关键词时应返回空列表")
        void noMatchKeywordShouldReturnEmpty() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), "不存在的关键词", 1, 20);

            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("不同项目应返回各自的集合列表")
        void differentProjectsShouldReturnIndependentResults() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    2002L, null, 1, 20);

            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("分页查询应支持页码和每页条数")
        void pageQueryShouldSupportPagination() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 1, 2);

            assertEquals(3, result.getTotal());
            assertEquals(2, result.getRecords().size());
            assertEquals(1, result.getCurrent());
            assertEquals(2, result.getSize());
        }

        @Test
        @DisplayName("第二页应返回剩余记录")
        void secondPageShouldReturnRemaining() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 2, 2);

            assertEquals(3, result.getTotal());
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("pageNo 为 null 时应使用默认值 1")
        void nullPageNoShouldDefaultToOne() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, null, 20);

            assertEquals(1, result.getCurrent());
            assertEquals(3, result.getTotal());
        }

        @Test
        @DisplayName("pageSize 为 null 时应使用默认值 20")
        void nullPageSizeShouldDefaultToTwenty() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 1, null);

            assertEquals(20, result.getSize());
            assertEquals(3, result.getTotal());
        }

        @Test
        @DisplayName("projectId 为 null 时应查询所有项目的集合")
        void nullProjectIdShouldReturnAllProjects() {
            // 在其他项目下再创建一个集合
            collectionService.create(createDTO, 2002L, testUserId());

            IPage<CollectionVO> result = collectionService.pageQuery(
                    null, null, 1, 20);

            assertTrue(result.getTotal() >= 4);
        }

        @Test
        @DisplayName("结果应包含文件夹数量和集合项数量统计")
        void resultShouldIncludeFolderAndItemCounts() {
            // 为第一个集合添加文件夹和项
            CollectionVO first = collectionService.pageQuery(testProjectId(), null, 1, 20)
                    .getRecords().get(0);

            CollectionFolderEntity folder = new CollectionFolderEntity();
            folder.setCollectionId(first.getId());
            folder.setName("测试文件夹");
            folder.setSortOrder(1);
            folderMapper.insert(folder);

            ApiDefinitionEntity def = insertApiDefinition();

            CollectionItemEntity item = new CollectionItemEntity();
            item.setCollectionId(first.getId());
            item.setFolderId(folder.getId());
            item.setDefinitionId(def.getId());
            item.setName("测试项");
            item.setEnabled(1);
            item.setSortOrder(1);
            itemMapper.insert(item);

            // 重新查询并验证统计
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), null, 1, 20);

            CollectionVO updated = result.getRecords().stream()
                    .filter(v -> v.getId().equals(first.getId()))
                    .findFirst().orElseThrow();
            assertTrue(updated.getFolderCount() >= 1);
            assertTrue(updated.getItemCount() >= 1);
        }

        @Test
        @DisplayName("关键词为空字符串时不应过滤")
        void emptyKeywordShouldNotFilter() {
            IPage<CollectionVO> result = collectionService.pageQuery(
                    testProjectId(), "", 1, 20);

            assertEquals(3, result.getTotal());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private ApiDefinitionEntity insertApiDefinition() {
        return insertApiDefinition("GET", "/api/users");
    }

    private ApiDefinitionEntity insertApiDefinition(String method, String path) {
        ApiDefinitionEntity def = new ApiDefinitionEntity();
        def.setProjectId(testProjectId());
        def.setName("示例接口");
        def.setPath(path);
        def.setMethod(method);
        def.setStatus("DRAFT");
        def.setVersion("1.0.0");
        definitionMapper.insert(def);
        return def;
    }
}