package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupUpdateDTO;
import com.hfwas.devops.apitest.apidefine.service.ApiGroupService;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import com.hfwas.devops.common.core.base.BaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ApiGroupController REST API 测试
 * <p>
 * 继承 BaseApiTest 以使用 SQLite 内存数据库、事务自动回滚、MockMvc 注入。
 * 覆盖所有 REST 端点：创建、更新、删除、获取树、获取详情。
 * 使用 MockMvc 模拟 HTTP 请求，验证状态码、响应结构、业务数据。
 *
 * @author hfwas
 */
@DisplayName("ApiGroupController — 接口分组 REST API 测试")
class ApiGroupControllerTest extends BaseApiTest {

    @Autowired
    private ApiGroupService apiGroupService;

    private static final String BASE_URL = "/apitest/groups";

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private ApiGroupCreateDTO buildCreateDTO(String name, Integer sortOrder, String description) {
        ApiGroupCreateDTO dto = new ApiGroupCreateDTO();
        dto.setProjectId(testProjectId());
        dto.setName(name);
        dto.setSortOrder(sortOrder);
        dto.setDescription(description);
        return dto;
    }

    private ApiGroupUpdateDTO buildUpdateDTO(String name, Integer sortOrder, String description) {
        ApiGroupUpdateDTO dto = new ApiGroupUpdateDTO();
        dto.setName(name);
        dto.setSortOrder(sortOrder);
        dto.setDescription(description);
        return dto;
    }

    private <T> BaseResult<T> parseBaseResult(MvcResult result, Class<T> dataType) throws Exception {
        String json = result.getResponse().getContentAsString();
        return fromJson(json, BaseResult.class);
    }

    // ========================================================================
    // POST /apitest/groups 创建分组
    // ========================================================================

    @Nested
    @DisplayName("POST /apitest/groups 创建分组")
    class CreateGroup {

        @Test
        @DisplayName("创建根级分组应返回 200 和完整 VO")
        void createRootGroupShouldReturnOk() throws Exception {
            ApiGroupCreateDTO dto = buildCreateDTO("用户管理", 1, "用户相关接口分组");

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNotEmpty())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<ApiGroupVO> baseResult = fromJson(json, BaseResult.class);
            ApiGroupVO vo = objectMapper.convertValue(baseResult.getData(), ApiGroupVO.class);

            assertNotNull(vo.getId());
            assertEquals("用户管理", vo.getName());
            assertEquals(testProjectId(), vo.getProjectId());
            assertNull(vo.getParentId());
            assertEquals(1, vo.getSortOrder());
            assertEquals("用户相关接口分组", vo.getDescription());
            assertNotNull(vo.getCreateTime());
        }

        @Test
        @DisplayName("创建子级分组应正确关联父级 ID")
        void createChildGroupShouldLinkParent() throws Exception {
            // 先创建父分组
            ApiGroupVO parent = apiGroupService.create(buildCreateDTO("父分组", 1, "父分组"), testUserId());

            ApiGroupCreateDTO child = new ApiGroupCreateDTO();
            child.setProjectId(testProjectId());
            child.setParentId(parent.getId());
            child.setName("子分组");
            child.setSortOrder(1);

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(child)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<ApiGroupVO> baseResult = fromJson(json, BaseResult.class);
            ApiGroupVO vo = objectMapper.convertValue(baseResult.getData(), ApiGroupVO.class);

            assertNotNull(vo.getId());
            assertEquals(parent.getId(), vo.getParentId());
            assertEquals("子分组", vo.getName());
        }

        @Test
        @DisplayName("缺少必填字段(projectId)应返回业务错误码")
        void missingProjectIdShouldReturnBadRequest() throws Exception {
            ApiGroupCreateDTO dto = new ApiGroupCreateDTO();
            dto.setName("测试分组");
            dto.setSortOrder(1);

            mockMvc.perform(post(BASE_URL)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("缺少必填字段(name)应返回业务错误码")
        void missingNameShouldReturnBadRequest() throws Exception {
            ApiGroupCreateDTO dto = new ApiGroupCreateDTO();
            dto.setProjectId(testProjectId());
            dto.setSortOrder(1);

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
            ApiGroupCreateDTO dto = buildCreateDTO("用户管理", 1, null);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("创建同级同名分组应返回业务错误码")
        void duplicateNameShouldReturnBusinessError() throws Exception {
            // 先创建一个分组
            apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());

            ApiGroupCreateDTO duplicate = buildCreateDTO("用户管理", 2, null);

            mockMvc.perform(post(BASE_URL)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(duplicate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("空请求体应返回业务错误码")
        void emptyBodyShouldReturnBadRequest() throws Exception {
            mockMvc.perform(post(BASE_URL)
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
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not a json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // PUT /apitest/groups/{id} 更新分组
    // ========================================================================

    @Nested
    @DisplayName("PUT /apitest/groups/{id} 更新分组")
    class UpdateGroup {

        @Test
        @DisplayName("更新分组名称应返回 200 和更新后的数据")
        void updateNameShouldReturnOk() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());

            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理V2", 2, "更新描述");

            MvcResult result = mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<ApiGroupVO> baseResult = fromJson(json, BaseResult.class);
            ApiGroupVO vo = objectMapper.convertValue(baseResult.getData(), ApiGroupVO.class);

            assertEquals("用户管理V2", vo.getName());
            assertEquals(2, vo.getSortOrder());
            assertEquals("更新描述", vo.getDescription());
        }

        @Test
        @DisplayName("更新不存在的分组应返回业务错误码")
        void updateNonExistentGroupShouldReturnBusinessError() throws Exception {
            ApiGroupUpdateDTO dto = buildUpdateDTO("不存在", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", 99999L)
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("更新为同级同名分组应返回业务错误码")
        void updateToDuplicateNameShouldReturnBusinessError() throws Exception {
            apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());
            ApiGroupVO second = apiGroupService.create(buildCreateDTO("订单管理", 2, null), testUserId());

            ApiGroupUpdateDTO dto = buildUpdateDTO("用户管理", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", second.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少必填字段(name)应返回业务错误码")
        void updateMissingNameShouldReturnBadRequest() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());

            ApiGroupUpdateDTO dto = new ApiGroupUpdateDTO();
            dto.setSortOrder(1);

            mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("空请求体应返回业务错误码")
        void updateEmptyBodyShouldReturnBadRequest() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());

            mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .param("userId", String.valueOf(testUserId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }

        @Test
        @DisplayName("缺少 userId 请求参数应返回业务错误码")
        void updateMissingUserIdShouldReturnBadRequest() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());
            ApiGroupUpdateDTO dto = buildUpdateDTO("新名称", 1, null);

            mockMvc.perform(put(BASE_URL + "/{id}", created.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // DELETE /apitest/groups/{id} 删除分组
    // ========================================================================

    @Nested
    @DisplayName("DELETE /apitest/groups/{id} 删除分组")
    class DeleteGroup {

        @Test
        @DisplayName("删除无依赖分组应返回 200")
        void deleteGroupWithoutDependenciesShouldReturnOk() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("临时分组", 1, null), testUserId());

            mockMvc.perform(delete(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("删除存在子分组的分组应返回业务错误码")
        void deleteGroupWithChildrenShouldReturnBusinessError() throws Exception {
            ApiGroupVO parent = apiGroupService.create(buildCreateDTO("父分组", 1, null), testUserId());

            ApiGroupCreateDTO child = new ApiGroupCreateDTO();
            child.setProjectId(testProjectId());
            child.setParentId(parent.getId());
            child.setName("子分组");
            child.setSortOrder(1);
            apiGroupService.create(child, testUserId());

            mockMvc.perform(delete(BASE_URL + "/{id}", parent.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("删除不存在的分组应返回业务错误码")
        void deleteNonExistentGroupShouldReturnBusinessError() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", 99999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ========================================================================
    // GET /apitest/groups/tree 获取分组树
    // ========================================================================

    @Nested
    @DisplayName("GET /apitest/groups/tree 获取分组树")
    class GetGroupTree {

        @Test
        @DisplayName("项目下无分组时应返回空列表")
        void noGroupsShouldReturnEmptyList() throws Exception {
            MvcResult result = mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<List<ApiGroupVO>> baseResult = fromJson(json, BaseResult.class);
            List<ApiGroupVO> data = objectMapper.convertValue(
                    baseResult.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApiGroupVO.class));
            assertTrue(data.isEmpty());
        }

        @Test
        @DisplayName("仅有根级分组时应返回扁平列表")
        void onlyRootGroupsShouldReturnFlatList() throws Exception {
            apiGroupService.create(buildCreateDTO("分组A", 1, null), testUserId());
            apiGroupService.create(buildCreateDTO("分组B", 2, null), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<List<ApiGroupVO>> baseResult = fromJson(json, BaseResult.class);
            List<ApiGroupVO> data = objectMapper.convertValue(
                    baseResult.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApiGroupVO.class));
            assertTrue(data.stream().allMatch(g -> g.getChildren() == null || g.getChildren().isEmpty()));
        }

        @Test
        @DisplayName("多级分组应返回正确树形结构")
        void multiLevelGroupsShouldReturnTreeStructure() throws Exception {
            ApiGroupVO root = apiGroupService.create(buildCreateDTO("根分组", 1, null), testUserId());

            ApiGroupCreateDTO child = new ApiGroupCreateDTO();
            child.setProjectId(testProjectId());
            child.setParentId(root.getId());
            child.setName("子分组");
            child.setSortOrder(1);
            apiGroupService.create(child, testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("根分组"))
                    .andExpect(jsonPath("$.data[0].children[0].name").value("子分组"))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<List<ApiGroupVO>> baseResult = fromJson(json, BaseResult.class);
            List<ApiGroupVO> data = objectMapper.convertValue(
                    baseResult.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApiGroupVO.class));
            assertEquals(1, data.size());
            assertEquals("根分组", data.get(0).getName());
            assertNotNull(data.get(0).getChildren());
            assertEquals(1, data.get(0).getChildren().size());
            assertEquals("子分组", data.get(0).getChildren().get(0).getName());
        }

        @Test
        @DisplayName("分组树应按 sortOrder 升序排列")
        void treeShouldBeOrderedBySortOrder() throws Exception {
            apiGroupService.create(buildCreateDTO("分组B", 3, null), testUserId());
            apiGroupService.create(buildCreateDTO("分组A", 1, null), testUserId());
            apiGroupService.create(buildCreateDTO("分组C", 2, null), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("分组A"))
                    .andExpect(jsonPath("$.data[1].name").value("分组C"))
                    .andExpect(jsonPath("$.data[2].name").value("分组B"))
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<List<ApiGroupVO>> baseResult = fromJson(json, BaseResult.class);
            List<ApiGroupVO> data = objectMapper.convertValue(
                    baseResult.getData(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApiGroupVO.class));
            assertEquals("分组A", data.get(0).getName());
            assertEquals("分组C", data.get(1).getName());
            assertEquals("分组B", data.get(2).getName());
        }

        @Test
        @DisplayName("不同项目应返回各自独立的树")
        void differentProjectsShouldReturnIndependentTrees() throws Exception {
            apiGroupService.create(buildCreateDTO("项目1分组", 1, null), testUserId());

            ApiGroupCreateDTO otherProject = new ApiGroupCreateDTO();
            otherProject.setProjectId(2002L);
            otherProject.setName("项目2分组");
            otherProject.setSortOrder(1);
            apiGroupService.create(otherProject, testUserId());

            mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("项目1分组"));

            mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", "2002"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("项目2分组"));
        }

        @Test
        @DisplayName("分组树中应包含 apiCount 统计")
        void treeShouldIncludeApiCount() throws Exception {
            ApiGroupVO root = apiGroupService.create(buildCreateDTO("根分组", 1, null), testUserId());

            // 通过 service 创建子分组（不关联接口）
            ApiGroupCreateDTO child = new ApiGroupCreateDTO();
            child.setProjectId(testProjectId());
            child.setParentId(root.getId());
            child.setName("子分组");
            child.setSortOrder(1);
            apiGroupService.create(child, testUserId());

            mockMvc.perform(get(BASE_URL + "/tree")
                            .param("projectId", String.valueOf(testProjectId())))
                    .andExpect(jsonPath("$.data[0].apiCount").isNumber())
                    .andExpect(jsonPath("$.data[0].children[0].apiCount").isNumber());
        }

        @Test
        @DisplayName("缺少 projectId 参数应返回业务错误码")
        void missingProjectIdShouldReturnBadRequest() throws Exception {
            mockMvc.perform(get(BASE_URL + "/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    // ========================================================================
    // GET /apitest/groups/{id} 获取分组详情
    // ========================================================================

    @Nested
    @DisplayName("GET /apitest/groups/{id} 获取分组详情")
    class GetDetail {

        @Test
        @DisplayName("查询存在的分组应返回完整详情")
        void getExistingGroupShouldReturnDetail() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, "用户相关接口分组"), testUserId());

            MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(created.getId()))
                    .andExpect(jsonPath("$.data.name").value("用户管理"))
                    .andExpect(jsonPath("$.data.projectId").value(testProjectId()))
                    .andExpect(jsonPath("$.data.sortOrder").value(1))
                    .andExpect(jsonPath("$.data.description").value("用户相关接口分组"))
                    .andExpect(jsonPath("$.data.createTime").isNotEmpty())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            BaseResult<ApiGroupVO> baseResult = fromJson(json, BaseResult.class);
            ApiGroupVO vo = objectMapper.convertValue(baseResult.getData(), ApiGroupVO.class);
            assertEquals(testProjectId(), vo.getProjectId());
            assertNotNull(vo.getCreateTime());
        }

        @Test
        @DisplayName("查询不存在的分组应返回业务错误码")
        void getNonExistentGroupShouldReturnBusinessError() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{id}", 99999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("查询子分组详情应正确返回父级 ID")
        void getChildGroupDetailShouldReturnParentId() throws Exception {
            ApiGroupVO parent = apiGroupService.create(buildCreateDTO("父分组", 1, null), testUserId());

            ApiGroupCreateDTO child = new ApiGroupCreateDTO();
            child.setProjectId(testProjectId());
            child.setParentId(parent.getId());
            child.setName("子分组");
            child.setSortOrder(1);
            ApiGroupVO childVo = apiGroupService.create(child, testUserId());

            mockMvc.perform(get(BASE_URL + "/{id}", childVo.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.parentId").value(parent.getId()));
        }

        @Test
        @DisplayName("多次查询同一分组应返回一致结果")
        void multipleQueriesShouldReturnConsistentResult() throws Exception {
            ApiGroupVO created = apiGroupService.create(buildCreateDTO("用户管理", 1, null), testUserId());

            MvcResult result1 = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            MvcResult result2 = mockMvc.perform(get(BASE_URL + "/{id}", created.getId()))
                    .andExpect(status().isOk())
                    .andReturn();

            BaseResult<ApiGroupVO> br1 = fromJson(result1.getResponse().getContentAsString(), BaseResult.class);
            BaseResult<ApiGroupVO> br2 = fromJson(result2.getResponse().getContentAsString(), BaseResult.class);

            ApiGroupVO vo1 = objectMapper.convertValue(br1.getData(), ApiGroupVO.class);
            ApiGroupVO vo2 = objectMapper.convertValue(br2.getData(), ApiGroupVO.class);

            assertEquals(vo1.getId(), vo2.getId());
            assertEquals(vo1.getName(), vo2.getName());
            assertEquals(vo1.getSortOrder(), vo2.getSortOrder());
        }
    }
}