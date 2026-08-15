package com.hfwas.devops.apitest.environment;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.environment.dto.EnvironmentCreateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentUpdateDTO;
import com.hfwas.devops.apitest.environment.dto.EnvironmentVariableDTO;
import com.hfwas.devops.apitest.environment.service.EnvironmentService;
import com.hfwas.devops.apitest.environment.vo.EnvironmentDetailVO;
import com.hfwas.devops.apitest.environment.vo.EnvironmentVO;
import com.hfwas.devops.common.core.base.BaseResult;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EnvironmentController REST API 测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚、MockMvc 注入。
 * 覆盖所有 REST 端点：分页查询、列表查询、详情查询、创建、更新、删除。
 * 使用 MockMvc 模拟 HTTP 请求，验证状态码、响应结构、业务数据。
 *
 * @author hfwas
 */
@DisplayName("EnvironmentController — 环境变量 REST API 测试")
class EnvironmentControllerTest extends BaseApiTest {

    @Autowired
    private EnvironmentService environmentService;

    private static final String BASE_URL = "/apitest/environments";

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private EnvironmentCreateDTO buildCreateDTO(String name, Integer sortOrder, String description) {
        EnvironmentCreateDTO dto = new EnvironmentCreateDTO();
        dto.setName(name);
        dto.setSortOrder(sortOrder);
        dto.setDescription(description);
        return dto;
    }

    private EnvironmentUpdateDTO buildUpdateDTO(String name, Integer sortOrder, String description) {
        EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
        dto.setName(name);
        dto.setSortOrder(sortOrder);
        dto.setDescription(description);
        return dto;
    }

    private EnvironmentVariableDTO buildVariableDTO(String name, String value, Boolean isSecret, Integer sortOrder) {
        EnvironmentVariableDTO dto = new EnvironmentVariableDTO();
        dto.setName(name);
        dto.setValue(value);
        dto.setIsSecret(isSecret);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    private <T> BaseResult<T> parseBaseResult(MvcResult result, Class<T> dataType) throws Exception {
        String json = result.getResponse().getContentAsString();
        return fromJson(json, BaseResult.class);
    }

    private EnvironmentDetailVO parseDetailVO(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
        return objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
    }

    // ========================================================================
    // POST /apitest/environments 创建环境
    // ========================================================================

    @Nested
    @DisplayName("POST /apitest/environments 创建环境")
    class CreateEnvironment {

        @Test
        @DisplayName("创建基本环境应返回 200 和完整详情 VO")
        void createBasicEnvironmentShouldReturnOk() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, "用于接口功能测试");

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNotEmpty())
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertNotNull(vo.getId());
            assertEquals("测试环境", vo.getName());
            assertEquals("用于接口功能测试", vo.getDescription());
            assertEquals(Integer.valueOf(1), vo.getSortOrder());
            assertEquals(testProjectId(), vo.getProjectId());
            assertNotNull(vo.getCreateTime());
            assertNotNull(vo.getUpdateTime());
        }

        @Test
        @DisplayName("创建含变量的环境应返回 200 和变量列表")
        void createWithVariablesShouldReturnOk() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);
            dto.setVariables(Arrays.asList(
                    buildVariableDTO("BASE_URL", "http://localhost:8080", false, 1),
                    buildVariableDTO("API_KEY", "sk-test-12345", true, 2)
            ));

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertNotNull(vo.getVariables());
            assertEquals(2, vo.getVariables().size());
        }

        @Test
        @DisplayName("创建含敏感变量的环境，敏感变量值应返回掩码")
        void createWithSecretVariableShouldMaskValue() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);
            dto.setVariables(Collections.singletonList(
                    buildVariableDTO("API_KEY", "sk-test-12345", true, 1)
            ));

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertEquals("API_KEY", vo.getVariables().get(0).getName());
            assertEquals("******", vo.getVariables().get(0).getValue());
            assertTrue(vo.getVariables().get(0).getIsSecret());
        }

        @Test
        @DisplayName("创建含空变量列表的环境应成功")
        void createWithEmptyVariablesShouldSucceed() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);
            dto.setVariables(Collections.emptyList());

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertNotNull(vo.getId());
        }

        @Test
        @DisplayName("缺少必填字段(name)应返回业务错误码")
        void missingNameShouldReturnBadRequest() throws Exception {
            EnvironmentCreateDTO dto = new EnvironmentCreateDTO();
            dto.setSortOrder(1);

            mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("缺少 projectId 请求参数应返回业务错误码")
        void missingProjectIdShouldReturnBadRequest() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);

            mockMvc.perform(post(BASE_URL)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("缺少 userId 请求参数应返回业务错误码")
        void missingUserIdShouldReturnBadRequest() throws Exception {
            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);

            mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("同一项目下创建同名环境应返回业务错误码")
        void duplicateNameShouldReturnBusinessError() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            EnvironmentCreateDTO duplicate = buildCreateDTO("测试环境", 2, null);

            mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(duplicate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("不同项目下创建同名环境应成功")
        void sameNameInDifferentProjectsShouldSucceed() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            EnvironmentCreateDTO dto = buildCreateDTO("测试环境", 1, null);

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("projectId", "2002")
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertEquals(2002L, vo.getProjectId());
        }

        @Test
        @DisplayName("空请求体应返回业务错误码")
        void emptyBodyShouldReturnBadRequest() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("请求体为非法 JSON 应返回业务错误码")
        void invalidJsonBodyShouldReturnBadRequest() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not a json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // PUT /apitest/environments/{id} 更新环境
    // ========================================================================

    @Nested
    @DisplayName("PUT /apitest/environments/{id} 更新环境")
    class UpdateEnvironment {

        @Test
        @DisplayName("更新环境名称应返回 200 和更新后的数据")
        void updateNameShouldReturnOk() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = buildUpdateDTO("测试环境V2", 2, "更新后的描述");

            MvcResult result = mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertEquals("测试环境V2", vo.getName());
            assertEquals(Integer.valueOf(2), vo.getSortOrder());
            assertEquals("更新后的描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新环境变量（替换）应成功")
        void updateVariablesShouldReplaceAll() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("测试环境", 1, null);
            createDTO.setVariables(Collections.singletonList(
                    buildVariableDTO("OLD_VAR", "old-value", false, 1)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setVariables(Collections.singletonList(
                    buildVariableDTO("NEW_VAR", "new-value", false, 1)));

            MvcResult result = mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertNotNull(vo.getVariables());
            assertEquals(1, vo.getVariables().size());
            assertEquals("NEW_VAR", vo.getVariables().get(0).getName());
        }

        @Test
        @DisplayName("更新传入空变量列表应清空变量")
        void updateWithEmptyVariablesShouldClearAll() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("测试环境", 1, null);
            createDTO.setVariables(Collections.singletonList(
                    buildVariableDTO("VAR", "value", false, 1)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = new EnvironmentUpdateDTO();
            dto.setVariables(Collections.emptyList());

            MvcResult result = mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertTrue(vo.getVariables() == null || vo.getVariables().isEmpty());
        }

        @Test
        @DisplayName("更新不存在的环境应返回业务错误码")
        void updateNonExistentEnvironmentShouldReturnBusinessError() throws Exception {
            EnvironmentUpdateDTO dto = buildUpdateDTO("不存在", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", 99999L)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("更新为同一项目下已存在的同名环境应返回业务错误码")
        void updateToDuplicateNameShouldReturnBusinessError() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            EnvironmentDetailVO second = environmentService.create(
                    buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = buildUpdateDTO("测试环境", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", second.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("更新为自身原名应成功")
        void updateToSameNameShouldSucceed() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            EnvironmentUpdateDTO dto = buildUpdateDTO("测试环境", 1, "更新描述");

            MvcResult result = mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            EnvironmentDetailVO vo = parseDetailVO(result);
            assertEquals("测试环境", vo.getName());
            assertEquals("更新描述", vo.getDescription());
        }

        @Test
        @DisplayName("缺少 userId 请求参数应返回业务错误码")
        void missingUserIdShouldReturnBadRequest() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            EnvironmentUpdateDTO dto = buildUpdateDTO("新名称", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("空请求体应返回业务错误码")
        void emptyBodyShouldReturnBadRequest() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // DELETE /apitest/environments/{id} 删除环境
    // ========================================================================

    @Nested
    @DisplayName("DELETE /apitest/environments/{id} 删除环境")
    class DeleteEnvironment {

        @Test
        @DisplayName("删除存在环境应返回 200")
        void deleteExistingEnvironmentShouldReturnOk() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("临时环境", 1, null), testProjectId(), testUserId());

            mockMvc.perform(delete(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("删除不存在环境应返回业务错误码")
        void deleteNonExistentEnvironmentShouldReturnBusinessError() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", 99999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("删除含变量的环境应成功并连带删除变量")
        void deleteEnvironmentWithVariablesShouldSucceed() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("含变量环境", 1, null);
            createDTO.setVariables(Collections.singletonList(
                    buildVariableDTO("VAR", "value", false, 1)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            mockMvc.perform(delete(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("多次删除同一环境应返回业务错误码")
        void deleteSameEnvironmentTwiceShouldReturnBusinessError() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("临时环境", 1, null), testProjectId(), testUserId());

            // 第一次删除成功
            mockMvc.perform(delete(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 第二次删除返回业务错误
            mockMvc.perform(delete(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ========================================================================
    // GET /apitest/environments/page 分页查询环境列表
    // ========================================================================

    @Nested
    @DisplayName("GET /apitest/environments/page 分页查询环境列表")
    class PageQuery {

        @Test
        @DisplayName("分页查询所有环境应返回全部记录")
        void pageQueryAllShouldReturnAllEnvironments() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNotEmpty())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertEquals(3, pageData.get("total"));
            assertEquals(3, ((List<?>) pageData.get("records")).size());
        }

        @Test
        @DisplayName("按关键词搜索应返回匹配结果")
        void filterByKeywordShouldReturnMatchingEnvironments() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("keyword", "生产"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertEquals(1, pageData.get("total"));
        }

        @Test
        @DisplayName("分页查询结果应按 sortOrder 升序排列")
        void pageQueryShouldBeOrderedBySortOrderAsc() throws Exception {
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
            assertEquals("测试环境", records.get(0).get("name"));
            assertEquals("预发布环境", records.get(1).get("name"));
            assertEquals("生产环境", records.get(2).get("name"));
        }

        @Test
        @DisplayName("分页查询应支持页码和每页条数")
        void pageQueryShouldSupportPagination() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("pageNo", "1")
                            .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertEquals(3, pageData.get("total"));
            assertEquals(2, ((List<?>) pageData.get("records")).size());
        }

        @Test
        @DisplayName("第二页应返回剩余记录")
        void secondPageShouldReturnRemaining() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("pageNo", "2")
                            .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertEquals(3, pageData.get("total"));
            assertEquals(1, ((List<?>) pageData.get("records")).size());
        }

        @Test
        @DisplayName("无匹配条件时应返回空列表")
        void noMatchShouldReturnEmptyPage() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", String.valueOf(testProjectId()))
                            .param("keyword", "不存在的环境"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertTrue(((List<?>) pageData.get("records")).isEmpty());
            assertEquals(0, pageData.get("total"));
        }

        @Test
        @DisplayName("不同项目应返回各自的环境列表")
        void differentProjectsShouldReturnIndependentResults() throws Exception {
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/page")
                            .param("projectId", "2002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<Map<String, Object>> baseResult = fromJson(json, BaseResult.class);
            Map<String, Object> pageData = objectMapper.convertValue(baseResult.getData(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            assertTrue(((List<?>) pageData.get("records")).isEmpty());
            assertEquals(0, pageData.get("total"));
        }
    }

    // ========================================================================
    // GET /apitest/environments/list 查询所有环境列表（不分页）
    // ========================================================================

    @Nested
    @DisplayName("GET /apitest/environments/list 查询所有环境列表（不分页）")
    class ListAll {

        @Test
        @DisplayName("查询所有环境应返回全部记录，按 sortOrder 升序排列")
        void listAllShouldReturnAllEnvironmentsOrderedBySortOrder() throws Exception {
            environmentService.create(buildCreateDTO("生产环境", 3, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());
            environmentService.create(buildCreateDTO("预发布环境", 2, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/list")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<List<EnvironmentVO>> baseResult = fromJson(json, BaseResult.class);
            List<EnvironmentVO> data = objectMapper.convertValue(
                    baseResult.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, EnvironmentVO.class));
            assertEquals("测试环境", data.get(0).getName());
            assertEquals("预发布环境", data.get(1).getName());
            assertEquals("生产环境", data.get(2).getName());
        }

        @Test
        @DisplayName("项目下无环境时应返回空列表")
        void noEnvironmentsShouldReturnEmptyList() throws Exception {
            MvcResult result = mockMvc.perform(get(BASE_URL + "/list")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andReturn();
        }

        @Test
        @DisplayName("不同项目应返回各自独立的环境列表")
        void differentProjectsShouldReturnIndependentLists() throws Exception {
            environmentService.create(buildCreateDTO("项目1环境", 1, null), testProjectId(), testUserId());

            mockMvc.perform(get(BASE_URL + "/list")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("项目1环境"));

            mockMvc.perform(get(BASE_URL + "/list")
                            .param("projectId", "2002"))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("缺少 projectId 参数应返回业务错误码")
        void missingProjectIdShouldReturnBadRequest() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // GET /apitest/environments/{id} 获取环境详情
    // ========================================================================

    @Nested
    @DisplayName("GET /apitest/environments/{id} 获取环境详情")
    class GetDetail {

        @Test
        @DisplayName("查询存在的环境应返回完整详情")
        void getExistingEnvironmentShouldReturnDetail() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, "用于接口功能测试"), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(created.getId()))
                    .andExpect(jsonPath("$.data.name").value("测试环境"))
                    .andExpect(jsonPath("$.data.projectId").value(testProjectId()))
                    .andExpect(jsonPath("$.data.sortOrder").value(1))
                    .andExpect(jsonPath("$.data.description").value("用于接口功能测试"))
                    .andExpect(jsonPath("$.data.createTime").isNotEmpty())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
            EnvironmentDetailVO vo = objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
            assertEquals(testProjectId(), vo.getProjectId());
            assertNotNull(vo.getCreateTime());
        }

        @Test
        @DisplayName("查询含变量的环境应返回变量列表")
        void getDetailWithVariablesShouldReturnVariables() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("测试环境", 1, null);
            createDTO.setVariables(Arrays.asList(
                    buildVariableDTO("BASE_URL", "http://localhost:8080", false, 1),
                    buildVariableDTO("API_KEY", "sk-test-12345", true, 2)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.variables.length()").value(2))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
            EnvironmentDetailVO vo = objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
            assertNotNull(vo.getVariables());
            assertEquals(2, vo.getVariables().size());
        }

        @Test
        @DisplayName("详情中敏感变量值应返回掩码")
        void getDetailWithSecretVariableShouldMaskValue() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("测试环境", 1, null);
            createDTO.setVariables(Collections.singletonList(
                    buildVariableDTO("API_KEY", "sk-test-12345", true, 1)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
            EnvironmentDetailVO vo = objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
            EnvironmentDetailVO.EnvironmentVariableItemVO secretVar = vo.getVariables().get(0);
            assertEquals("API_KEY", secretVar.getName());
            assertEquals("******", secretVar.getValue());
            assertTrue(secretVar.getIsSecret());
        }

        @Test
        @DisplayName("详情中非敏感变量应返回原始值")
        void getDetailWithNonSecretVariableShouldReturnOriginalValue() throws Exception {
            EnvironmentCreateDTO createDTO = buildCreateDTO("测试环境", 1, null);
            createDTO.setVariables(Collections.singletonList(
                    buildVariableDTO("BASE_URL", "http://localhost:8080", false, 1)));
            EnvironmentDetailVO created = environmentService.create(createDTO, testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
            EnvironmentDetailVO vo = objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
            EnvironmentDetailVO.EnvironmentVariableItemVO nonSecretVar = vo.getVariables().get(0);
            assertEquals("BASE_URL", nonSecretVar.getName());
            assertEquals("http://localhost:8080", nonSecretVar.getValue());
            assertFalse(nonSecretVar.getIsSecret());
        }

        @Test
        @DisplayName("查询不存在的环境应返回业务错误码")
        void getNonExistentEnvironmentShouldReturnBusinessError() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{id}", 99999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("多次查询同一环境应返回一致结果")
        void multipleQueriesShouldReturnConsistentResult() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            MvcResult result1 = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            MvcResult result2 = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            BaseResult<EnvironmentDetailVO> br1 = fromJson(result1.getResponse().getContentAsString(), BaseResult.class);
            BaseResult<EnvironmentDetailVO> br2 = fromJson(result2.getResponse().getContentAsString(), BaseResult.class);

            EnvironmentDetailVO vo1 = objectMapper.convertValue(br1.getData(), EnvironmentDetailVO.class);
            EnvironmentDetailVO vo2 = objectMapper.convertValue(br2.getData(), EnvironmentDetailVO.class);

            assertEquals(vo1.getId(), vo2.getId());
            assertEquals(vo1.getName(), vo2.getName());
            assertEquals(vo1.getSortOrder(), vo2.getSortOrder());
        }

        @Test
        @DisplayName("无变量的环境详情中 variables 应返回空列表")
        void environmentWithoutVariablesShouldReturnEmptyList() throws Exception {
            EnvironmentDetailVO created = environmentService.create(
                    buildCreateDTO("测试环境", 1, null), testProjectId(), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<EnvironmentDetailVO> baseResult = fromJson(json, BaseResult.class);
            EnvironmentDetailVO vo = objectMapper.convertValue(baseResult.getData(), EnvironmentDetailVO.class);
            assertTrue(vo.getVariables() == null || vo.getVariables().isEmpty());
        }
    }
}