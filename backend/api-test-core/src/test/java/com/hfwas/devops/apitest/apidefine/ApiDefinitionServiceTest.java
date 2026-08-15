package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionParamDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionQueryDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionResponseDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionUpdateDTO;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionParamEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionResponseEntity;
import com.hfwas.devops.apitest.apidefine.entity.ApiDefinitionVersionEntity;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionParamMapper;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionResponseMapper;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionVersionMapper;
import com.hfwas.devops.apitest.apidefine.service.ApiDefinitionService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionDetailVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionVO;
import com.hfwas.devops.apitest.common.exception.ApiTestException;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
 * ApiDefinitionService 业务逻辑测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚和自动填充。
 * 覆盖接口定义 CRUD、分页查询、状态流转（发布/废弃/恢复草稿）、
 * 路径+方法唯一性校验、参数/响应关联保存等核心逻辑。
 *
 * @author hfwas
 */
@DisplayName("ApiDefinitionService — 接口定义业务测试")
class ApiDefinitionServiceTest extends BaseApiTest {

    @Autowired
    private ApiDefinitionService apiDefinitionService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private ApiDefinitionParamMapper paramMapper;

    @Autowired
    private ApiDefinitionResponseMapper responseMapper;

    @Autowired
    private ApiDefinitionVersionMapper versionMapper;

    private ApiDefinitionCreateDTO createDTO;
    private ApiDefinitionParamDTO queryParamDTO;
    private ApiDefinitionParamDTO headerParamDTO;
    private ApiDefinitionResponseDTO successResponseDTO;
    private ApiDefinitionResponseDTO errorResponseDTO;

    @BeforeEach
    void setUp() {
        createDTO = new ApiDefinitionCreateDTO();
        createDTO.setProjectId(testProjectId());
        createDTO.setGroupId(1001L);
        createDTO.setName("获取用户列表");
        createDTO.setPath("/api/users");
        createDTO.setMethod("GET");
        createDTO.setTags(Arrays.asList("用户", "查询"));
        createDTO.setDescription("分页获取用户列表");
        createDTO.setProtocol("HTTP");
        createDTO.setHost("localhost:8080");
        createDTO.setContentType("application/json");

        queryParamDTO = new ApiDefinitionParamDTO();
        queryParamDTO.setParamType("query");
        queryParamDTO.setName("page");
        queryParamDTO.setDataType("integer");
        queryParamDTO.setRequired(true);
        queryParamDTO.setDescription("页码");
        queryParamDTO.setSortOrder(1);

        headerParamDTO = new ApiDefinitionParamDTO();
        headerParamDTO.setParamType("header");
        headerParamDTO.setName("Authorization");
        headerParamDTO.setDataType("string");
        headerParamDTO.setRequired(true);
        headerParamDTO.setDescription("认证令牌");
        headerParamDTO.setSortOrder(2);

        successResponseDTO = new ApiDefinitionResponseDTO();
        successResponseDTO.setStatusCode(200);
        successResponseDTO.setContentType("application/json");
        successResponseDTO.setDescription("成功响应");

        errorResponseDTO = new ApiDefinitionResponseDTO();
        errorResponseDTO.setStatusCode(400);
        errorResponseDTO.setContentType("application/json");
        errorResponseDTO.setDescription("错误响应");
    }

    // ========================================================================
    // create 创建接口定义
    // ========================================================================

    @Nested
    @DisplayName("create 创建接口定义")
    class CreateApiDefinition {

        @Test
        @DisplayName("创建基本接口定义应成功并返回完整详情VO")
        void createBasicApiShouldSucceed() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());

            assertNotNull(vo);
            assertNotNull(vo.getId());
            assertEquals("获取用户列表", vo.getName());
            assertEquals("/api/users", vo.getPath());
            assertEquals("GET", vo.getMethod());
            assertEquals(testProjectId(), vo.getProjectId());
            assertEquals(1001L, vo.getGroupId());
            assertEquals("DRAFT", vo.getStatus());
            assertEquals("1.0.0", vo.getVersion());
            assertEquals("分页获取用户列表", vo.getDescription());
            assertEquals("HTTP", vo.getProtocol());
            assertEquals("localhost:8080", vo.getHost());
            assertEquals("application/json", vo.getContentType());
            assertNotNull(vo.getCreateTime());
            assertEquals(testUserId(), vo.getCreatedBy());
        }

        @Test
        @DisplayName("创建后数据库应保存正确的记录")
        void createShouldPersistToDatabase() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(vo.getId());
            assertNotNull(saved);
            assertEquals("获取用户列表", saved.getName());
            assertEquals("/api/users", saved.getPath());
            assertEquals("GET", saved.getMethod());
            assertEquals(testProjectId(), saved.getProjectId());
            assertEquals("DRAFT", saved.getStatus());
            assertEquals("1.0.0", saved.getVersion());
            assertEquals(0, saved.getDeleted().intValue());
        }

        @Test
        @DisplayName("创建后审计字段应自动填充")
        void createShouldAutoFillAuditFields() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(vo.getId());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getCreateBy());
            assertEquals(testUserId(), saved.getUpdateBy());
        }

        @Test
        @DisplayName("创建含参数和响应的接口应成功")
        void createWithParamsAndResponsesShouldSucceed() {
            createDTO.setParams(Arrays.asList(queryParamDTO, headerParamDTO));
            createDTO.setResponses(Arrays.asList(successResponseDTO, errorResponseDTO));

            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());

            // 验证参数
            assertNotNull(vo.getParams());
            assertEquals(2, vo.getParams().size());
            assertEquals("query", vo.getParams().get(0).getParamType());
            assertEquals("header", vo.getParams().get(1).getParamType());

            // 验证响应
            assertNotNull(vo.getResponses());
            assertEquals(2, vo.getResponses().size());
            assertEquals(200, vo.getResponses().get(0).getStatusCode());
            assertEquals(400, vo.getResponses().get(1).getStatusCode());

            // 验证数据库持久化
            List<ApiDefinitionParamEntity> params = paramMapper.selectList(null);
            assertEquals(2, params.size());
            assertTrue(params.stream().allMatch(p -> p.getDefinitionId().equals(vo.getId())));

            List<ApiDefinitionResponseEntity> responses = responseMapper.selectList(null);
            assertEquals(2, responses.size());
            assertTrue(responses.stream().allMatch(r -> r.getDefinitionId().equals(vo.getId())));
        }

        @Test
        @DisplayName("创建含空参数和响应的接口应成功")
        void createWithEmptyParamsAndResponsesShouldSucceed() {
            createDTO.setParams(new ArrayList<>());
            createDTO.setResponses(new ArrayList<>());

            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            assertNotNull(vo);
            assertNotNull(vo.getId());
        }

        @Test
        @DisplayName("tags 为空列表时创建应成功")
        void createWithEmptyTagsShouldSucceed() {
            createDTO.setTags(new ArrayList<>());

            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            assertNotNull(vo.getId());
            assertTrue(vo.getTags() == null || vo.getTags().isEmpty());
        }

        @Test
        @DisplayName("groupId 为 null 时创建应成功")
        void createWithNullGroupIdShouldSucceed() {
            createDTO.setGroupId(null);

            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            assertNotNull(vo.getId());
            assertNull(vo.getGroupId());
        }

        @Test
        @DisplayName("同一项目下相同路径+方法创建应抛出 ApiTestException")
        void duplicatePathMethodShouldThrow() {
            apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionCreateDTO duplicate = new ApiDefinitionCreateDTO();
            duplicate.setProjectId(testProjectId());
            duplicate.setName("获取用户列表V2");
            duplicate.setPath("/api/users");
            duplicate.setMethod("GET");

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.create(duplicate, testUserId()));
            assertTrue(ex.getMessage().contains("相同路径"));
        }

        @Test
        @DisplayName("同一项目下相同路径不同方法应允许创建")
        void samePathDifferentMethodShouldBeAllowed() {
            apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionCreateDTO postApi = new ApiDefinitionCreateDTO();
            postApi.setProjectId(testProjectId());
            postApi.setName("创建用户");
            postApi.setPath("/api/users");
            postApi.setMethod("POST");

            ApiDefinitionDetailVO vo = apiDefinitionService.create(postApi, testUserId());
            assertNotNull(vo.getId());
        }

        @Test
        @DisplayName("不同项目下相同路径+方法应允许创建")
        void samePathMethodInDifferentProjectsShouldBeAllowed() {
            apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionCreateDTO otherProject = new ApiDefinitionCreateDTO();
            otherProject.setProjectId(2002L);
            otherProject.setName("获取用户列表");
            otherProject.setPath("/api/users");
            otherProject.setMethod("GET");

            ApiDefinitionDetailVO vo = apiDefinitionService.create(otherProject, testUserId());
            assertNotNull(vo.getId());
        }
    }

    // ========================================================================
    // update 更新接口定义
    // ========================================================================

    @Nested
    @DisplayName("update 更新接口定义")
    class UpdateApiDefinition {

        private Long definitionId;

        @BeforeEach
        void insertApi() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            definitionId = vo.getId();
        }

        private ApiDefinitionUpdateDTO buildUpdateDTO(String name, String path, String method) {
            ApiDefinitionUpdateDTO dto = new ApiDefinitionUpdateDTO();
            dto.setName(name);
            dto.setPath(path);
            dto.setMethod(method);
            dto.setGroupId(1001L);
            return dto;
        }

        @Test
        @DisplayName("更新接口名称应成功")
        void updateNameShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表V2", "/api/users", "GET");
            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());

            assertEquals("获取用户列表V2", vo.getName());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(definitionId);
            assertEquals("获取用户列表V2", saved.getName());
        }

        @Test
        @DisplayName("更新接口路径应成功")
        void updatePathShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users/v2", "GET");
            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());

            assertEquals("/api/users/v2", vo.getPath());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(definitionId);
            assertEquals("/api/users/v2", saved.getPath());
        }

        @Test
        @DisplayName("更新接口方法应成功")
        void updateMethodShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users", "POST");
            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());

            assertEquals("POST", vo.getMethod());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(definitionId);
            assertEquals("POST", saved.getMethod());
        }

        @Test
        @DisplayName("更新接口描述应成功")
        void updateDescriptionShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users", "GET");
            dto.setDescription("更新后的描述");

            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());
            assertEquals("更新后的描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新接口分组应成功")
        void updateGroupIdShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users", "GET");
            dto.setGroupId(2001L);

            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());
            assertEquals(2001L, vo.getGroupId());
        }

        @Test
        @DisplayName("更新接口 tags 应成功")
        void updateTagsShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users", "GET");
            dto.setTags(Arrays.asList("新标签1", "新标签2"));

            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());
            assertEquals(2, vo.getTags().size());
            assertTrue(vo.getTags().contains("新标签1"));
        }

        @Test
        @DisplayName("更新接口参数（先删后插）应成功")
        void updateParamsShouldReplaceAll() {
            // 先创建含参数的接口，使用不同路径避免与 @BeforeEach 冲突
            createDTO.setPath("/api/users-params");
            createDTO.setParams(Arrays.asList(queryParamDTO));
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            // 更新参数为新的列表
            ApiDefinitionUpdateDTO updateDTO = new ApiDefinitionUpdateDTO();
            updateDTO.setName("获取用户列表");
            updateDTO.setPath("/api/users-params");
            updateDTO.setMethod("GET");
            updateDTO.setGroupId(1001L);

            ApiDefinitionParamDTO newParam = new ApiDefinitionParamDTO();
            newParam.setParamType("query");
            newParam.setName("pageSize");
            newParam.setDataType("integer");
            newParam.setRequired(true);
            newParam.setDescription("每页条数");
            newParam.setSortOrder(1);
            updateDTO.setParams(List.of(newParam));

            apiDefinitionService.update(created.getId(), updateDTO, testUserId());

            // 验证旧参数已被删除，新参数已插入
            List<ApiDefinitionParamEntity> params = paramMapper.selectList(null);
            assertEquals(1, params.size());
            assertEquals("pageSize", params.get(0).getName());
        }

        @Test
        @DisplayName("更新接口响应（先删后插）应成功")
        void updateResponsesShouldReplaceAll() {
            // 先创建含响应的接口，使用不同路径避免与 @BeforeEach 冲突
            createDTO.setPath("/api/users-responses");
            createDTO.setResponses(Arrays.asList(successResponseDTO));
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            // 更新响应为新的列表
            ApiDefinitionUpdateDTO updateDTO = new ApiDefinitionUpdateDTO();
            updateDTO.setName("获取用户列表");
            updateDTO.setPath("/api/users-responses");
            updateDTO.setMethod("GET");
            updateDTO.setGroupId(1001L);

            ApiDefinitionResponseDTO newResponse = new ApiDefinitionResponseDTO();
            newResponse.setStatusCode(500);
            newResponse.setContentType("application/json");
            newResponse.setDescription("服务器错误");
            updateDTO.setResponses(List.of(newResponse));

            apiDefinitionService.update(created.getId(), updateDTO, testUserId());

            // 验证旧响应已被删除，新响应已插入
            List<ApiDefinitionResponseEntity> responses = responseMapper.selectList(null);
            assertEquals(1, responses.size());
            assertEquals(500, responses.get(0).getStatusCode());
        }

        @Test
        @DisplayName("更新为同一项目下已存在的路径+方法应抛出 ApiTestException")
        void updateToDuplicatePathMethodShouldThrow() {
            // 创建另一个接口
            ApiDefinitionCreateDTO another = new ApiDefinitionCreateDTO();
            another.setProjectId(testProjectId());
            another.setName("创建用户");
            another.setPath("/api/users");
            another.setMethod("POST");
            apiDefinitionService.create(another, testUserId());

            // 尝试将第一个接口的路径+方法改为和第二个相同
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表", "/api/users", "POST");
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.update(definitionId, dto, testUserId()));
            assertTrue(ex.getMessage().contains("相同路径"));
        }

        @Test
        @DisplayName("更新自身的路径+方法为原名不应抛出异常")
        void updateToSamePathMethodShouldSucceed() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表V2", "/api/users", "GET");
            ApiDefinitionDetailVO vo = apiDefinitionService.update(definitionId, dto, testUserId());

            assertEquals("获取用户列表V2", vo.getName());
        }

        @Test
        @DisplayName("更新不存在的接口应抛出 ApiTestException")
        void updateNonExistentApiShouldThrow() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("不存在", "/api/none", "GET");
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.update(99999L, dto, testUserId()));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("更新后 updateBy 和 updateTime 应自动填充")
        void updateShouldAutoFillAuditFields() {
            ApiDefinitionUpdateDTO dto = buildUpdateDTO("获取用户列表V2", "/api/users", "GET");
            apiDefinitionService.update(definitionId, dto, testUserId());

            ApiDefinitionEntity saved = apiDefinitionMapper.selectById(definitionId);
            assertNotNull(saved.getUpdateTime());
            assertEquals(testUserId(), saved.getUpdateBy());
        }
    }

    // ========================================================================
    // getDetail 获取接口详情
    // ========================================================================

    @Nested
    @DisplayName("getDetail 获取接口详情")
    class GetDetail {

        @Test
        @DisplayName("查询存在的接口应返回完整详情VO")
        void getExistingApiShouldReturnDetail() {
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionDetailVO detail = apiDefinitionService.getDetail(created.getId());
            assertNotNull(detail);
            assertEquals(created.getId(), detail.getId());
            assertEquals("获取用户列表", detail.getName());
            assertEquals("/api/users", detail.getPath());
            assertEquals("GET", detail.getMethod());
            assertEquals(testProjectId(), detail.getProjectId());
            assertEquals("DRAFT", detail.getStatus());
            assertEquals("1.0.0", detail.getVersion());
            assertNotNull(detail.getCreateTime());
            assertEquals(testUserId(), detail.getCreatedBy());
        }

        @Test
        @DisplayName("查询含参数和响应的接口应返回完整详情")
        void getDetailWithParamsAndResponses() {
            createDTO.setParams(Arrays.asList(queryParamDTO, headerParamDTO));
            createDTO.setResponses(Arrays.asList(successResponseDTO));
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionDetailVO detail = apiDefinitionService.getDetail(created.getId());
            assertNotNull(detail.getParams());
            assertEquals(2, detail.getParams().size());
            assertNotNull(detail.getResponses());
            assertEquals(1, detail.getResponses().size());
        }

        @Test
        @DisplayName("查询不存在的接口应抛出 ApiTestException")
        void getNonExistentApiShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.getDetail(99999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("查询无参数无响应的接口应返回空列表")
        void getApiWithoutParamsAndResponses() {
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionDetailVO detail = apiDefinitionService.getDetail(created.getId());
            assertTrue(detail.getParams() == null || detail.getParams().isEmpty());
            assertTrue(detail.getResponses() == null || detail.getResponses().isEmpty());
        }

        @Test
        @DisplayName("多次查询同一接口应返回一致结果")
        void multipleQueriesShouldReturnConsistentResult() {
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionDetailVO detail1 = apiDefinitionService.getDetail(created.getId());
            ApiDefinitionDetailVO detail2 = apiDefinitionService.getDetail(created.getId());

            assertEquals(detail1.getId(), detail2.getId());
            assertEquals(detail1.getName(), detail2.getName());
            assertEquals(detail1.getPath(), detail2.getPath());
        }
    }

    // ========================================================================
    // delete 删除接口定义
    // ========================================================================

    @Nested
    @DisplayName("delete 删除接口定义")
    class DeleteApiDefinition {

        private Long definitionId;

        @BeforeEach
        void insertApi() {
            createDTO.setParams(Arrays.asList(queryParamDTO));
            createDTO.setResponses(Arrays.asList(successResponseDTO));
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            definitionId = vo.getId();
        }

        @Test
        @DisplayName("删除接口应成功并连带删除参数和响应")
        void deleteShouldRemoveApiAndRelatedData() {
            apiDefinitionService.delete(definitionId);

            // 主表记录被逻辑删除
            ApiDefinitionEntity deleted = apiDefinitionMapper.selectById(definitionId);
            assertNull(deleted);

            // 参数和响应也被删除
            List<ApiDefinitionParamEntity> params = paramMapper.selectList(null);
            assertTrue(params.isEmpty());

            List<ApiDefinitionResponseEntity> responses = responseMapper.selectList(null);
            assertTrue(responses.isEmpty());
        }

        @Test
        @DisplayName("删除不存在的接口应抛出 ApiTestException")
        void deleteNonExistentApiShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.delete(99999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    // ========================================================================
    // pageQuery 分页查询接口列表
    // ========================================================================

    @Nested
    @DisplayName("pageQuery 分页查询接口列表")
    class PageQuery {

        @BeforeEach
        void insertTestData() {
            apiDefinitionService.create(createDTO, testUserId());

            ApiDefinitionCreateDTO createApi = new ApiDefinitionCreateDTO();
            createApi.setProjectId(testProjectId());
            createApi.setName("创建用户");
            createApi.setPath("/api/users");
            createApi.setMethod("POST");
            createApi.setTags(Arrays.asList("用户", "创建"));
            apiDefinitionService.create(createApi, testUserId());

            ApiDefinitionCreateDTO deleteApi = new ApiDefinitionCreateDTO();
            deleteApi.setProjectId(testProjectId());
            deleteApi.setName("删除用户");
            deleteApi.setPath("/api/users/{id}");
            deleteApi.setMethod("DELETE");
            deleteApi.setTags(Arrays.asList("用户", "删除"));
            apiDefinitionService.create(deleteApi, testUserId());
        }

        @Test
        @DisplayName("分页查询所有接口应返回全部记录")
        void pageQueryAllShouldReturnAllApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertNotNull(result);
            assertEquals(3, result.getTotal());
            assertEquals(3, result.getRecords().size());
        }

        @Test
        @DisplayName("按分组筛选应返回正确结果")
        void filterByGroupIdShouldReturnCorrectApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setGroupId(1001L);

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertNotNull(result);
            assertTrue(result.getTotal() >= 1);
            assertTrue(result.getRecords().stream().allMatch(r -> r.getGroupId().equals(1001L)));
        }

        @Test
        @DisplayName("按关键词搜索应返回匹配结果")
        void filterByKeywordShouldReturnMatchingApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("创建");

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(1, result.getTotal());
            assertEquals("创建用户", result.getRecords().get(0).getName());
        }

        @Test
        @DisplayName("按关键词搜索路径应返回匹配结果")
        void filterByKeywordOnPathShouldReturnMatchingApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setKeyword("{id}");

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(1, result.getTotal());
            assertEquals("/api/users/{id}", result.getRecords().get(0).getPath());
        }

        @Test
        @DisplayName("按请求方法筛选应返回正确结果")
        void filterByMethodShouldReturnCorrectApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setMethod("GET");

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(1, result.getTotal());
            assertEquals("GET", result.getRecords().get(0).getMethod());
        }

        @Test
        @DisplayName("按状态筛选应返回正确结果")
        void filterByStatusShouldReturnCorrectApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setStatus("DRAFT");

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(3, result.getTotal());
            assertTrue(result.getRecords().stream().allMatch(r -> "DRAFT".equals(r.getStatus())));
        }

        @Test
        @DisplayName("按标签筛选应返回交集匹配的结果")
        void filterByTagsShouldReturnMatchingApis() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setTags(List.of("用户"));

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertTrue(result.getTotal() >= 3);
        }

        @Test
        @DisplayName("分页查询应返回结果按更新时间降序排列")
        void pageQueryShouldBeOrderedByUpdateTimeDesc() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            List<ApiDefinitionVO> records = result.getRecords();
            for (int i = 1; i < records.size(); i++) {
                assertTrue(
                        records.get(i - 1).getUpdateTime().compareTo(records.get(i).getUpdateTime()) >= 0
                );
            }
        }

        @Test
        @DisplayName("分页查询应支持页码和每页条数")
        void pageQueryShouldSupportPagination() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setPageNo(1);
            query.setPageSize(2);

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(3, result.getTotal());
            assertEquals(2, result.getRecords().size());
            assertEquals(1, result.getCurrent());
            assertEquals(2, result.getSize());
        }

        @Test
        @DisplayName("第二页应返回剩余记录")
        void secondPageShouldReturnRemaining() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setPageNo(2);
            query.setPageSize(2);

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertEquals(3, result.getTotal());
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("无匹配条件时应返回空列表")
        void noMatchShouldReturnEmptyPage() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(testProjectId());
            query.setMethod("PATCH");

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("不同项目应返回各自的接口列表")
        void differentProjectsShouldReturnIndependentResults() {
            ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
            query.setProjectId(2002L);

            IPage<ApiDefinitionVO> result = apiDefinitionService.pageQuery(query);
            assertTrue(result.getRecords().isEmpty());
        }
    }

    // ========================================================================
    // publish 发布接口
    // ========================================================================

    @Nested
    @DisplayName("publish 发布接口")
    class PublishApi {

        private Long definitionId;

        @BeforeEach
        void insertDraftApi() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            definitionId = vo.getId();
        }

        @Test
        @DisplayName("从草稿发布接口应成功并更新状态为 PUBLISHED")
        void publishDraftShouldSucceed() {
            apiDefinitionService.publish(definitionId, testUserId());

            ApiDefinitionEntity entity = apiDefinitionMapper.selectById(definitionId);
            assertEquals("PUBLISHED", entity.getStatus());
        }

        @Test
        @DisplayName("从草稿发布时应创建版本快照")
        void publishDraftShouldCreateVersionSnapshot() {
            apiDefinitionService.publish(definitionId, testUserId());

            List<ApiDefinitionVersionEntity> versions = versionMapper.selectList(null);
            assertEquals(1, versions.size());
            assertEquals(definitionId, versions.get(0).getDefinitionId());
            assertEquals("1.0.1", versions.get(0).getVersion());
            assertEquals("获取用户列表", versions.get(0).getSnapshotName());
            assertEquals("/api/users", versions.get(0).getSnapshotPath());
            assertEquals("GET", versions.get(0).getSnapshotMethod());
        }

        @Test
        @DisplayName("发布后版本号应递增")
        void publishShouldIncrementVersion() {
            apiDefinitionService.publish(definitionId, testUserId());

            ApiDefinitionEntity entity = apiDefinitionMapper.selectById(definitionId);
            assertEquals("1.0.1", entity.getVersion());
        }

        @Test
        @DisplayName("连续发布不会重复创建版本快照（仅草稿→已发布时触发）")
        void publishAlreadyPublishedShouldNotCreateSnapshot() {
            apiDefinitionService.publish(definitionId, testUserId());
            long versionCountAfterFirstPublish = versionMapper.selectCount(null);

            // 再次发布（已发布状态）
            apiDefinitionService.publish(definitionId, testUserId());

            long versionCountAfterSecondPublish = versionMapper.selectCount(null);
            assertEquals(versionCountAfterFirstPublish, versionCountAfterSecondPublish);
        }

        @Test
        @DisplayName("发布不存在的接口应抛出 ApiTestException")
        void publishNonExistentApiShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.publish(99999L, testUserId()));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    // ========================================================================
    // deprecate 废弃接口
    // ========================================================================

    @Nested
    @DisplayName("deprecate 废弃接口")
    class DeprecateApi {

        private Long publishedId;

        @BeforeEach
        void insertPublishedApi() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            apiDefinitionService.publish(vo.getId(), testUserId());
            publishedId = vo.getId();
        }

        @Test
        @DisplayName("废弃已发布的接口应成功并更新状态为 DEPRECATED")
        void deprecatePublishedShouldSucceed() {
            apiDefinitionService.deprecate(publishedId, testUserId());

            ApiDefinitionEntity entity = apiDefinitionMapper.selectById(publishedId);
            assertEquals("DEPRECATED", entity.getStatus());
        }

        @Test
        @DisplayName("废弃草稿状态的接口应抛出 ApiTestException")
        void deprecateDraftShouldThrow() {
            ApiDefinitionCreateDTO draftDTO = new ApiDefinitionCreateDTO();
            draftDTO.setProjectId(testProjectId());
            draftDTO.setGroupId(1001L);
            draftDTO.setName("获取草稿");
            draftDTO.setPath("/api/draft");
            draftDTO.setMethod("POST");
            draftDTO.setProtocol("HTTP");
            draftDTO.setHost("localhost:8080");
            draftDTO.setContentType("application/json");
            ApiDefinitionDetailVO draft = apiDefinitionService.create(draftDTO, testUserId());

            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.deprecate(draft.getId(), testUserId()));
            assertTrue(ex.getMessage().contains("仅已发布状态可废弃"));
        }

        @Test
        @DisplayName("废弃不存在的接口应抛出 ApiTestException")
        void deprecateNonExistentApiShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.deprecate(99999L, testUserId()));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    // ========================================================================
    // revertToDraft 恢复草稿
    // ========================================================================

    @Nested
    @DisplayName("revertToDraft 恢复草稿")
    class RevertToDraft {

        @Test
        @DisplayName("将已发布的接口恢复为草稿应成功")
        void revertPublishedToDraftShouldSucceed() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            apiDefinitionService.publish(vo.getId(), testUserId());

            apiDefinitionService.revertToDraft(vo.getId(), testUserId());

            ApiDefinitionEntity entity = apiDefinitionMapper.selectById(vo.getId());
            assertEquals("DRAFT", entity.getStatus());
        }

        @Test
        @DisplayName("将已废弃的接口恢复为草稿应成功")
        void revertDeprecatedToDraftShouldSucceed() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            apiDefinitionService.publish(vo.getId(), testUserId());
            apiDefinitionService.deprecate(vo.getId(), testUserId());

            apiDefinitionService.revertToDraft(vo.getId(), testUserId());

            ApiDefinitionEntity entity = apiDefinitionMapper.selectById(vo.getId());
            assertEquals("DRAFT", entity.getStatus());
        }

        @Test
        @DisplayName("恢复草稿时已发布的版本快照应保留")
        void revertToDraftShouldPreserveVersionHistory() {
            ApiDefinitionDetailVO vo = apiDefinitionService.create(createDTO, testUserId());
            apiDefinitionService.publish(vo.getId(), testUserId());
            long versionCount = versionMapper.selectCount(null);

            apiDefinitionService.revertToDraft(vo.getId(), testUserId());

            long versionCountAfterRevert = versionMapper.selectCount(null);
            assertEquals(versionCount, versionCountAfterRevert);
        }

        @Test
        @DisplayName("恢复不存在的接口应抛出 ApiTestException")
        void revertNonExistentApiShouldThrow() {
            ApiTestException ex = assertThrows(ApiTestException.class,
                    () -> apiDefinitionService.revertToDraft(99999L, testUserId()));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    // ========================================================================
    // 完整生命周期测试
    // ========================================================================

    @Nested
    @DisplayName("完整生命周期 — 创建→发布→废弃→恢复草稿→更新")
    class FullLifecycle {

        @Test
        @DisplayName("完整的接口生命周期流转应正确")
        void fullLifecycleShouldWork() {
            // 创建
            ApiDefinitionDetailVO created = apiDefinitionService.create(createDTO, testUserId());
            assertNotNull(created.getId());
            assertEquals("DRAFT", created.getStatus());

            // 发布
            apiDefinitionService.publish(created.getId(), testUserId());
            ApiDefinitionEntity published = apiDefinitionMapper.selectById(created.getId());
            assertEquals("PUBLISHED", published.getStatus());

            // 废弃
            apiDefinitionService.deprecate(created.getId(), testUserId());
            ApiDefinitionEntity deprecated = apiDefinitionMapper.selectById(created.getId());
            assertEquals("DEPRECATED", deprecated.getStatus());

            // 恢复草稿
            apiDefinitionService.revertToDraft(created.getId(), testUserId());
            ApiDefinitionEntity reverted = apiDefinitionMapper.selectById(created.getId());
            assertEquals("DRAFT", reverted.getStatus());

            // 更新草稿
            ApiDefinitionUpdateDTO updateDTO = new ApiDefinitionUpdateDTO();
            updateDTO.setName("获取用户列表V2");
            updateDTO.setPath("/api/users");
            updateDTO.setMethod("GET");
            updateDTO.setGroupId(1001L);
            ApiDefinitionDetailVO updated = apiDefinitionService.update(created.getId(), updateDTO, testUserId());
            assertEquals("获取用户列表V2", updated.getName());
        }
    }
}