package com.hfwas.devops.apitest.environment;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.hfwas.devops.apitest.environment.dto.EnvironmentCreateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentQueryDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentUpdateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentVariableDTO;
import com.hfwas.devops.apitest.environment.entity.EnvironmentEntity;
import com.hfwas.devops.apitest.environment.entity.EnvironmentVariableEntity;
import com.hfwas.devops.apitest.environment.mapper.EnvironmentMapper;
import com.hfwas.devops.apitest.environment.mapper.EnvironmentVariableMapper;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.environment.vo.EnvironmentDetailVO;
import com.hfwas.devops.apitest.environment.vo.EnvironmentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnvironmentService 业务逻辑测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖环境 CRUD、分页查询、变量管理、环境名唯一性校验、敏感变量掩码等核心逻辑。
 *
 * @author hfwas
 */
@DisplayName("EnvironmentService — 环境业务测试")
class EnvironmentServiceTest extends BaseApiTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private EnvironmentMapper environmentMapper;

    @Autowired
    private EnvironmentVariableMapper variableMapper;

    private EnvironmentCreateDTO createDTO;
    private EnvironmentVariableDTO varDTO1;
    private EnvironmentVariableDTO varDTO2;

    @BeforeEach
    void setUp() {
        createDTO = new EnvironmentCreateDTO();
        createDTO.setName("测试环境");
        createDTO.setDescription("用于接口功能测试");
        createDTO.setSortOrder(1);

        varDTO1 = new EnvironmentVariableDTO();
        varDTO1.setName("BASE_URL");
        varDTO1.setValue("http://localhost:8080");
        varDTO1.setDescription("基础地址");
        varDTO1.setIsSecret(false);
        varDTO1.setSortOrder(1);

        varDTO2 = new EnvironmentVariableDTO();
        varDTO2.setName("API_KEY");
        varDTO2.setValue("sk-test-12345");
        varDTO2.setDescription("API密钥");
        varDTO2.setIsSecret(true);
        varDTO2.setSortOrder(2);
    }

    // ========================================================================
    // create 创建环境
    // ========================================================================

    @Nested
    @DisplayName("create 创建环境")
    class CreateEnvironment {

        @Test
        @DisplayName("创建基本环境应成功并返回完整详情VO")
        void createBasicEnvironmentShouldSucceed() {
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            assertNotNull(vo);
            assertNotNull(vo.getId());
            assertEquals("测试环境", vo.getName());
            assertEquals("用于接口功能测试", vo.getDescription());
            assertEquals(Integer.valueOf(1), vo.getSortOrder());
            assertEquals(testProjectId(), vo.getProjectId());
            assertNotNull(vo.getCreateTime());
            assertNotNull(vo.getUpdateTime());
        }

        @Test
        @DisplayName("创建后数据库应保存正确的记录")
        void createShouldPersistToDatabase() {
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentEntity saved = environmentMapper.selectById(vo.getId());
            assertNotNull(saved);
            assertEquals("测试环境", saved.getName());
            assertEquals("用于接口功能测试", saved.getDescription());
            assertEquals(Integer.valueOf(1), saved.getSortOrder());
            assertEquals(testProjectId(), saved.getProjectId());
            assertEquals(0, saved.getDeleted().intValue());
        }

        @Test
        @DisplayName("创建后审计字段应自动填充")
        void createShouldAutoFillAuditFields() {
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentEntity saved = environmentMapper.selectById(vo.getId());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
        }

        @Test
        @DisplayName("创建含变量的环境应成功并保存变量")
        void createWithVariablesShouldSucceed() {
            createDTO.setVariables(Arrays.asList(varDTO1, varDTO2));

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            assertNotNull(vo.getVariables());
            assertEquals(2, vo.getVariables().size());

            // 验证数据库持久化
            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertEquals(2, variables.size());
            assertTrue(variables.stream().allMatch(v -> v.getEnvironmentId().equals(vo.getId())));

            // 验证变量值
            EnvironmentVariableEntity baseUrl = variables.stream()
                    .filter(v -> "BASE_URL".equals(v.getName()))
                    .findFirst().orElse(null);
            assertNotNull(baseUrl);
            assertEquals("http://localhost:8080", baseUrl.getValue());
            assertEquals(0, baseUrl.getIsSecret().intValue());

            EnvironmentVariableEntity apiKey = variables.stream()
                    .filter(v -> "API_KEY".equals(v.getName()))
                    .findFirst().orElse(null);
            assertNotNull(apiKey);
            assertEquals("sk-test-12345", apiKey.getValue());
            assertEquals(1, apiKey.getIsSecret().intValue());
        }

        @Test
        @DisplayName("创建含敏感变量的环境，详情VO中敏感变量值应返回掩码")
        void createWithSecretVariableShouldMaskInDetail() {
            createDTO.setVariables(Collections.singletonList(varDTO2));

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            assertEquals(1, vo.getVariables().size());
            assertEquals("API_KEY", vo.getVariables().get(0).getName());
            assertEquals("******", vo.getVariables().get(0).getValue());
            assertTrue(vo.getVariables().get(0).getIsSecret());
        }

        @Test
        @DisplayName("创建含空变量列表的环境应成功")
        void createWithEmptyVariablesShouldSucceed() {
            createDTO.setVariables(Collections.emptyList());

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertNotNull(vo);
            assertNotNull(vo.getId());
        }

        @Test
        @DisplayName("sortOrder 为 null 时创建应默认 0")
        void createWithNullSortOrderShouldDefaultToZero() {
            createDTO.setSortOrder(null);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(Integer.valueOf(0), vo.getSortOrder());
        }

        @Test
        @DisplayName("description 为 null 时创建应成功")
        void createWithNullDescriptionShouldSucceed() {
            createDTO.setDescription(null);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertNull(vo.getDescription());
        }

        @Test
        @DisplayName("同一项目下同名环境创建应抛出 ApiTestException")
        void duplicateNameInSameProjectShouldThrow() {
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO duplicate = new EnvironmentCreateDTO();
            duplicate.setName("测试环境");

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> environmentService.create(duplicate, testProjectId(), testUserId()));
            assertTrue(ex.getMessage().contains("同名环境"));
        }

        @Test
        @DisplayName("不同项目下同名环境应允许创建")
        void sameNameInDifferentProjectsShouldBeAllowed() {
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO otherProject = new EnvironmentCreateDTO();
            otherProject.setName("测试环境");
            otherProject.setDescription("其他项目的测试环境");

            EnvironmentDetailVO vo = environmentService.create(otherProject, 2002L, testUserId());
            assertNotNull(vo.getId());
            assertEquals(2002L, vo.getProjectId());
        }

        @Test
        @DisplayName("创建后无变量时详情VO中变量列表应为空")
        void createWithoutVariablesShouldReturnEmptyVariableList() {
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());

            assertTrue(vo.getVariables() == null || vo.getVariables().isEmpty());
        }
    }

    // ========================================================================
    // update 更新环境
    // ========================================================================

    @Nested
    @DisplayName("update 更新环境")
    class UpdateEnvironment {

        private Long environmentId;

        @BeforeEach
        void insertEnvironment() {
            createDTO.setVariables(Arrays.asList(varDTO1));
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            environmentId = vo.getId();
        }

        @Test
        @DisplayName("更新环境名称应成功")
        void updateNameShouldSucceed() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("测试环境V2");

            EnvironmentDetailVO vo = environmentService.update(environmentId, dto, testUserId());
            assertEquals("测试环境V2", vo.getName());

            EnvironmentEntity saved = environmentMapper.selectById(environmentId);
            assertEquals("测试环境V2", saved.getName());
        }

        @Test
        @DisplayName("更新环境描述应成功")
        void updateDescriptionShouldSucceed() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setDescription("更新后的环境描述");

            EnvironmentDetailVO vo = environmentService.update(environmentId, dto, testUserId());
            assertEquals("更新后的环境描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新环境排序序号应成功")
        void updateSortOrderShouldSucceed() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setSortOrder(99);

            EnvironmentDetailVO vo = environmentService.update(environmentId, dto, testUserId());
            assertEquals(Integer.valueOf(99), vo.getSortOrder());
        }

        @Test
        @DisplayName("更新环境变量（先删后插）应成功")
        void updateVariablesShouldReplaceAll() {
            EnvironmentVariableDTO newVar = new EnvironmentVariableDTO();
            newVar.setName("NEW_VAR");
            newVar.setValue("new-value");
            newVar.setDescription("新变量");
            newVar.setIsSecret(false);
            newVar.setSortOrder(1);

            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setVariables(Collections.singletonList(newVar));

            EnvironmentDetailVO vo = environmentService.update(environmentId, dto, testUserId());

            // 验证旧变量被删除，新变量已插入
            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertEquals(1, variables.size());
            assertEquals("NEW_VAR", variables.get(0).getName());
            assertEquals("new-value", variables.get(0).getValue());

            // 验证返回的VO包含新变量
            assertNotNull(vo.getVariables());
            assertEquals(1, vo.getVariables().size());
            assertEquals("NEW_VAR", vo.getVariables().get(0).getName());
        }

        @Test
        @DisplayName("更新传入空变量列表应清空变量")
        void updateWithEmptyVariablesShouldClearAll() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setVariables(Collections.emptyList());

            environmentService.update(environmentId, dto, testUserId());

            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertTrue(variables.isEmpty());
        }

        @Test
        @DisplayName("更新不传入变量列表应保留原有变量")
        void updateWithoutVariablesShouldPreserveExisting() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("仅更新名称");

            environmentService.update(environmentId, dto, testUserId());

            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertEquals(1, variables.size());
            assertEquals("BASE_URL", variables.get(0).getName());
        }

        @Test
        @DisplayName("更新为同一项目下已存在的同名环境应抛出 ApiTestException")
        void updateToDuplicateNameShouldThrow() {
            // 创建另一个环境
            EnvironmentCreateDTO another = new EnvironmentCreateDTO();
            another.setName("预发布环境");
            environmentService.create(another, testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("预发布环境");

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> environmentService.update(environmentId, dto, testUserId()));
            assertTrue(ex.getMessage().contains("同名环境"));
        }

        @Test
        @DisplayName("更新为自身原名不应抛出异常")
        void updateToSameNameShouldSucceed() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("测试环境");
            dto.setDescription("更新描述");

            EnvironmentDetailVO vo = environmentService.update(environmentId, dto, testUserId());
            assertEquals("测试环境", vo.getName());
            assertEquals("更新描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新不存在的环境应抛出 ApiTestException")
        void updateNonExistentEnvironmentShouldThrow() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("不存在");

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> environmentService.update(99999L, dto, testUserId()));
            assertTrue(ex.getMessage().contains("环境不存在"));
        }

        @Test
        @DisplayName("更新后 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillAuditFields() {
            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setName("更新后的名称");

            environmentService.update(environmentId, dto, testUserId());

            EnvironmentEntity saved = environmentMapper.selectById(environmentId);
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getUpdateBy());
        }
    }

    // ========================================================================
    // getDetail 获取环境详情
    // ========================================================================

    @Nested
    @DisplayName("getDetail 获取环境详情")
    class GetDetail {

        @Test
        @DisplayName("查询存在的环境应返回完整详情VO")
        void getExistingEnvironmentShouldReturnDetail() {
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            assertNotNull(detail);
            assertEquals(created.getId(), detail.getId());
            assertEquals("测试环境", detail.getName());
            assertEquals("用于接口功能测试", detail.getDescription());
            assertEquals(Integer.valueOf(1), detail.getSortOrder());
            assertEquals(testProjectId(), detail.getProjectId());
            assertNotNull(detail.getCreateTime());
        }

        @Test
        @DisplayName("查询含变量的环境应返回变量列表")
        void getDetailWithVariablesShouldReturnVariables() {
            createDTO.setVariables(Arrays.asList(varDTO1, varDTO2));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            assertNotNull(detail.getVariables());
            assertEquals(2, detail.getVariables().size());
        }

        @Test
        @DisplayName("详情中敏感变量值应返回掩码")
        void getDetailWithSecretVariableShouldMaskValue() {
            createDTO.setVariables(Collections.singletonList(varDTO2));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            EnvironmentDetailVO.EnvironmentVariableItemVO secretVar = detail.getVariables().get(0);
            assertEquals("API_KEY", secretVar.getName());
            assertEquals("******", secretVar.getValue());
            assertTrue(secretVar.getIsSecret());
        }

        @Test
        @DisplayName("详情中非敏感变量应返回原始值")
        void getDetailWithNonSecretVariableShouldReturnOriginalValue() {
            createDTO.setVariables(Collections.singletonList(varDTO1));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            EnvironmentDetailVO.EnvironmentVariableItemVO nonSecretVar = detail.getVariables().get(0);
            assertEquals("BASE_URL", nonSecretVar.getName());
            assertEquals("http://localhost:8080", nonSecretVar.getValue());
            assertFalse(nonSecretVar.getIsSecret());
        }

        @Test
        @DisplayName("查询不存在的环境应抛出 ApiTestException")
        void getNonExistentEnvironmentShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> environmentService.getDetail(99999L));
            assertTrue(ex.getMessage().contains("环境不存在"));
        }

        @Test
        @DisplayName("多次查询同一环境应返回一致结果")
        void multipleQueriesShouldReturnConsistentResult() {
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentDetailVO detail1 = environmentService.getDetail(created.getId());
            EnvironmentDetailVO detail2 = environmentService.getDetail(created.getId());

            assertEquals(detail1.getId(), detail2.getId());
            assertEquals(detail1.getName(), detail2.getName());
            assertEquals(detail1.getDescription(), detail2.getDescription());
        }
    }

    // ========================================================================
    // delete 删除环境
    // ========================================================================

    @Nested
    @DisplayName("delete 删除环境")
    class DeleteEnvironment {

        private Long environmentId;

        @BeforeEach
        void insertEnvironment() {
            createDTO.setVariables(Arrays.asList(varDTO1, varDTO2));
            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            environmentId = vo.getId();
        }

        @Test
        @DisplayName("删除环境应成功并连带删除变量")
        void deleteShouldRemoveEnvironmentAndVariables() {
            environmentService.delete(environmentId);

            // 主表记录被逻辑删除
            EnvironmentEntity deleted = environmentMapper.selectById(environmentId);
            assertNull(deleted);

            // 变量也被删除
            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertTrue(variables.isEmpty());
        }

        @Test
        @DisplayName("逻辑删除后 selectById 应返回 null")
        void selectByIdAfterDeleteShouldReturnNull() {
            environmentService.delete(environmentId);

            EnvironmentEntity found = environmentMapper.selectById(environmentId);
            assertNull(found);
        }

        @Test
        @DisplayName("删除不存在的环境应抛出 ApiTestException")
        void deleteNonExistentEnvironmentShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> environmentService.delete(99999L));
            assertTrue(ex.getMessage().contains("环境不存在"));
        }
    }

    // ========================================================================
    // pageQuery 分页查询环境列表
    // ========================================================================

    @Nested
    @DisplayName("pageQuery 分页查询环境列表")
    class PageQuery {

        @BeforeEach
        void insertTestData() {
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO stagingDTO = new EnvironmentCreateDTO();
            stagingDTO.setName("预发布环境");
            stagingDTO.setDescription("预发布验证环境");
            stagingDTO.setSortOrder(2);
            environmentService.create(stagingDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO prodDTO = new EnvironmentCreateDTO();
            prodDTO.setName("生产环境");
            prodDTO.setDescription("线上生产环境");
            prodDTO.setSortOrder(3);
            environmentService.create(prodDTO, testProjectId(), testUserId());
        }

        @Test
        @DisplayName("分页查询所有环境应返回全部记录")
        void pageQueryAllShouldReturnAllEnvironments() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertNotNull(result);
            assertEquals(3, result.getTotal());
            assertEquals(3, result.getRecords().size());
        }

        @Test
        @DisplayName("按关键词搜索应返回匹配结果")
        void filterByKeywordShouldReturnMatchingEnvironments() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("生产");

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(1, result.getTotal());
            assertEquals("生产环境", result.getRecords().get(0).getName());
        }

        @Test
        @DisplayName("按关键词模糊搜索应返回匹配结果")
        void filterByKeywordLikeShouldReturnMatchingEnvironments() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("环境");

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(3, result.getTotal());
        }

        @Test
        @DisplayName("分页查询结果应按 sortOrder 升序排列")
        void pageQueryShouldBeOrderedBySortOrderAsc() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            List<EnvironmentVO> records = result.getRecords();
            for (int i = 1; i < records.size(); i++) {
                assertTrue(records.get(i - 1).getSortOrder() <= records.get(i).getSortOrder());
            }
        }

        @Test
        @DisplayName("分页查询应支持页码和每页条数")
        void pageQueryShouldSupportPagination() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setPageNo(1);
            query.setPageSize(2);

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(3, result.getTotal());
            assertEquals(2, result.getRecords().size());
            assertEquals(1, result.getCurrent());
            assertEquals(2, result.getSize());
        }

        @Test
        @DisplayName("第二页应返回剩余记录")
        void secondPageShouldReturnRemaining() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setPageNo(2);
            query.setPageSize(2);

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(3, result.getTotal());
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("无匹配条件时应返回空列表")
        void noMatchShouldReturnEmptyPage() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("不存在的环境");

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("不同项目应返回各自的接口列表")
        void differentProjectsShouldReturnIndependentResults() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(2002L);

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("查询结果中应包含变量数量统计")
        void pageQueryShouldIncludeVariableCount() {
            // 给生产环境添加变量，使用不同名称避免与 setUp 中创建的环境冲突
            EnvironmentCreateDTO prodWithVars = new EnvironmentCreateDTO();
            prodWithVars.setName("生产环境-带变量");
            prodWithVars.setSortOrder(4);
            prodWithVars.setVariables(Collections.singletonList(varDTO1));
            environmentService.create(prodWithVars, testProjectId(), testUserId());

            // 创建新查询，使用更具体的关键词
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("带变量");

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(1, result.getRecords().size());
            assertEquals(Integer.valueOf(1), result.getRecords().get(0).getVariableCount());
        }

        @Test
        @DisplayName("无变量的环境变量数量应为 0")
        void environmentWithoutVariablesShouldHaveZeroVariableCount() {
            EnvironmentQueryDTO query = new EnvironmentQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("测试环境");

            IPage<EnvironmentVO> result = environmentService.pageQuery(query);
            assertEquals(1, result.getRecords().size());
            assertEquals(Integer.valueOf(0), result.getRecords().get(0).getVariableCount());
        }
    }

    // ========================================================================
    // listAll 查询所有环境（不分页）
    // ========================================================================

    @Nested
    @DisplayName("listAll 查询所有环境（不分页）")
    class ListAll {

        @BeforeEach
        void insertTestData() {
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO stagingDTO = new EnvironmentCreateDTO();
            stagingDTO.setName("预发布环境");
            stagingDTO.setSortOrder(2);
            environmentService.create(stagingDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO prodDTO = new EnvironmentCreateDTO();
            prodDTO.setName("生产环境");
            prodDTO.setSortOrder(3);
            environmentService.create(prodDTO, testProjectId(), testUserId());
        }

        @Test
        @DisplayName("查询所有环境应返回全部记录，按 sortOrder 升序排列")
        void listAllShouldReturnAllEnvironmentsOrderedBySortOrder() {
            List<EnvironmentVO> result = environmentService.listAll(testProjectId());

            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("测试环境", result.get(0).getName());
            assertEquals("预发布环境", result.get(1).getName());
            assertEquals("生产环境", result.get(2).getName());
        }

        @Test
        @DisplayName("不同项目下 listAll 应返回各自的环境")
        void differentProjectsShouldReturnIndependentResults() {
            List<EnvironmentVO> result = environmentService.listAll(2002L);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("listAll 结果中不应包含变量数量（VO 无 variableCount 填充）")
        void listAllShouldNotIncludeVariableCount() {
            List<EnvironmentVO> result = environmentService.listAll(testProjectId());

            assertNotNull(result);
            for (EnvironmentVO vo : result) {
                assertNull(vo.getVariableCount());
            }
        }
    }

    // ========================================================================
    // getVariableMap 获取环境变量映射
    // ========================================================================

    @Nested
    @DisplayName("getVariableMap 获取环境变量映射")
    class GetVariableMap {

        @Test
        @DisplayName("查询存在的环境变量映射应返回 Map")
        void getVariableMapShouldReturnMap() {
            createDTO.setVariables(Arrays.asList(varDTO1, varDTO2));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            Map<String, String> variableMap = environmentService.getVariableMap(created.getId());

            assertNotNull(variableMap);
            assertEquals(2, variableMap.size());
            assertEquals("http://localhost:8080", variableMap.get("BASE_URL"));
            assertEquals("sk-test-12345", variableMap.get("API_KEY"));
        }

        @Test
        @DisplayName("无变量的环境应返回空 Map")
        void environmentWithoutVariablesShouldReturnEmptyMap() {
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            Map<String, String> variableMap = environmentService.getVariableMap(created.getId());

            assertNotNull(variableMap);
            assertTrue(variableMap.isEmpty());
        }

        @Test
        @DisplayName("environmentId 为 null 时应返回空 Map")
        void nullEnvironmentIdShouldReturnEmptyMap() {
            Map<String, String> variableMap = environmentService.getVariableMap(null);

            assertNotNull(variableMap);
            assertTrue(variableMap.isEmpty());
        }

        @Test
        @DisplayName("不存在的 environmentId 应返回空 Map")
        void nonExistentEnvironmentIdShouldReturnEmptyMap() {
            Map<String, String> variableMap = environmentService.getVariableMap(99999L);

            assertNotNull(variableMap);
            assertTrue(variableMap.isEmpty());
        }

        @Test
        @DisplayName("变量映射中敏感变量应返回原始值（getVariableMap 不掩码）")
        void getVariableMapShouldReturnOriginalValueForSecretVariables() {
            createDTO.setVariables(Collections.singletonList(varDTO2));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            Map<String, String> variableMap = environmentService.getVariableMap(created.getId());

            assertEquals("sk-test-12345", variableMap.get("API_KEY"));
        }
    }

    // ========================================================================
    // 完整生命周期测试
    // ========================================================================

    @Nested
    @DisplayName("完整生命周期 — 创建→更新→查询→删除")
    class FullLifecycle {

        @Test
        @DisplayName("完整的环境生命周期流转应正确")
        void fullLifecycleShouldWork() {
            // 创建
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());
            assertNotNull(created.getId());
            assertEquals("测试环境", created.getName());
            assertEquals(testProjectId(), created.getProjectId());

            // 更新
            EnvironmentUpdateDTO updateDTO = new EnvironmentUpdateDTO();
            updateDTO.setName("测试环境V2");
            updateDTO.setDescription("更新后的描述");
            EnvironmentDetailVO updated = environmentService.update(created.getId(), updateDTO, testUserId());
            assertEquals("测试环境V2", updated.getName());
            assertEquals("更新后的描述", updated.getDescription());

            // 查询详情
            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            assertEquals("测试环境V2", detail.getName());
            assertEquals("更新后的描述", detail.getDescription());

            // 删除
            environmentService.delete(created.getId());
            assertNull(environmentMapper.selectById(created.getId()));
        }

        @Test
        @DisplayName("创建含变量的环境→更新变量→查询详情→删除的完整流程")
        void fullLifecycleWithVariablesShouldWork() {
            // 创建含变量的环境
            createDTO.setVariables(Arrays.asList(varDTO1));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());
            assertNotNull(created.getId());
            assertEquals(1, created.getVariables().size());

            // 更新变量
            EnvironmentUpdateDTO updateDTO = new EnvironmentUpdateDTO();
            updateDTO.setVariables(Arrays.asList(varDTO1, varDTO2));
            EnvironmentDetailVO updated = environmentService.update(created.getId(), updateDTO, testUserId());
            assertEquals(2, updated.getVariables().size());

            // 查询详情并验证变量
            EnvironmentDetailVO detail = environmentService.getDetail(created.getId());
            assertEquals(2, detail.getVariables().size());

            // 获取变量映射
            Map<String, String> variableMap = environmentService.getVariableMap(created.getId());
            assertEquals(2, variableMap.size());

            // 删除
            environmentService.delete(created.getId());
            assertNull(environmentMapper.selectById(created.getId()));
            assertTrue(variableMapper.selectList(null).isEmpty());
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
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO otherProjectDTO = new EnvironmentCreateDTO();
            otherProjectDTO.setName("其他项目测试环境");
            otherProjectDTO.setSortOrder(1);
            environmentService.create(otherProjectDTO, 2002L, testUserId());

            List<EnvironmentVO> projectEnvs = environmentService.listAll(testProjectId());
            assertEquals(1, projectEnvs.size());
            assertEquals("测试环境", projectEnvs.get(0).getName());

            List<EnvironmentVO> otherProjectEnvs = environmentService.listAll(2002L);
            assertEquals(1, otherProjectEnvs.size());
            assertEquals("其他项目测试环境", otherProjectEnvs.get(0).getName());
        }

        @Test
        @DisplayName("同一项目下环境应按 sortOrder 升序排列")
        void environmentsShouldBeOrderedBySortOrderAsc() {
            EnvironmentCreateDTO low = new EnvironmentCreateDTO();
            low.setName("最低优先级");
            low.setSortOrder(10);
            environmentService.create(low, testProjectId(), testUserId());

            EnvironmentCreateDTO high = new EnvironmentCreateDTO();
            high.setName("最高优先级");
            high.setSortOrder(1);
            environmentService.create(high, testProjectId(), testUserId());

            EnvironmentCreateDTO mid = new EnvironmentCreateDTO();
            mid.setName("中间优先级");
            mid.setSortOrder(5);
            environmentService.create(mid, testProjectId(), testUserId());

            List<EnvironmentVO> result = environmentService.listAll(testProjectId());
            assertEquals(3, result.size());
            assertEquals("最高优先级", result.get(0).getName());
            assertEquals("中间优先级", result.get(1).getName());
            assertEquals("最低优先级", result.get(2).getName());
        }

        @Test
        @DisplayName("不同项目下同名环境应允许创建")
        void sameNameInDifferentProjectsShouldBeAllowed() {
            environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentCreateDTO sameNameProject = new EnvironmentCreateDTO();
            sameNameProject.setName("测试环境");
            sameNameProject.setSortOrder(1);

            EnvironmentDetailVO vo = environmentService.create(sameNameProject, 2002L, testUserId());
            assertNotNull(vo.getId());
        }
    }

    // ========================================================================
    // 边界条件
    // ========================================================================

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

        @Test
        @DisplayName("环境名称为超长字符串时创建与查询应正确")
        void veryLongNameShouldBeStoredCorrectly() {
            String longName = "测试环境" + "A".repeat(100);
            createDTO.setName(longName);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(longName, vo.getName());
        }

        @Test
        @DisplayName("环境描述为超长字符串时创建与查询应正确")
        void veryLongDescriptionShouldBeStoredCorrectly() {
            String longDesc = "描述" + "B".repeat(500);
            createDTO.setDescription(longDesc);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(longDesc, vo.getDescription());
        }

        @Test
        @DisplayName("sortOrder 为最小值时创建与查询应正确")
        void minSortOrderShouldBeStoredCorrectly() {
            createDTO.setSortOrder(Integer.MIN_VALUE);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(Integer.valueOf(Integer.MIN_VALUE), vo.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为最大值时创建与查询应正确")
        void maxSortOrderShouldBeStoredCorrectly() {
            createDTO.setSortOrder(Integer.MAX_VALUE);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), vo.getSortOrder());
        }

        @Test
        @DisplayName("创建大量变量应成功")
        void createWithManyVariablesShouldSucceed() {
            List<EnvironmentVariableDTO> manyVars = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) {
                EnvironmentVariableDTO v = new EnvironmentVariableDTO();
                v.setName("VAR_" + i);
                v.setValue("value_" + i);
                v.setSortOrder(i);
                manyVars.add(v);
            }
            createDTO.setVariables(manyVars);

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals(50, vo.getVariables().size());

            List<EnvironmentVariableEntity> variables = variableMapper.selectList(null);
            assertEquals(50, variables.size());
        }

        @Test
        @DisplayName("变量值包含特殊字符应正确存储")
        void variableValueWithSpecialCharactersShouldBeStoredCorrectly() {
            EnvironmentVariableDTO specialVar = new EnvironmentVariableDTO();
            specialVar.setName("SPECIAL");
            specialVar.setValue("!@#$%^&*()_+-=[]{}|;':\",./<>?`~");
            specialVar.setSortOrder(1);

            createDTO.setVariables(Collections.singletonList(specialVar));

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals("!@#$%^&*()_+-=[]{}|;':\",./<>?`~", vo.getVariables().get(0).getValue());
        }

        @Test
        @DisplayName("变量名为空字符串时创建应成功")
        void variableWithEmptyNameShouldBeStoredCorrectly() {
            EnvironmentVariableDTO emptyNameVar = new EnvironmentVariableDTO();
            emptyNameVar.setName("");
            emptyNameVar.setValue("value");
            emptyNameVar.setSortOrder(1);

            createDTO.setVariables(Collections.singletonList(emptyNameVar));

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals("", vo.getVariables().get(0).getName());
        }

        @Test
        @DisplayName("变量值为空时创建应成功")
        void variableWithEmptyValueShouldBeStoredCorrectly() {
            EnvironmentVariableDTO emptyValueVar = new EnvironmentVariableDTO();
            emptyValueVar.setName("EMPTY_VAL");
            emptyValueVar.setValue("");
            emptyValueVar.setSortOrder(1);

            createDTO.setVariables(Collections.singletonList(emptyValueVar));

            EnvironmentDetailVO vo = environmentService.create(createDTO, testProjectId(), testUserId());
            assertEquals("", vo.getVariables().get(0).getValue());
        }
    }
}