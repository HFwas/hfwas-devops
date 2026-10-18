package com.hfwas.devops.apitest;

import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionCreateDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiDefinitionQueryDTO;
import com.hfwas.devops.apitest.apidefine.dto.ApiGroupCreateDTO;
import com.hfwas.devops.apitest.apidefine.mapper.ApiDefinitionMapper;
import com.hfwas.devops.apitest.apidefine.service.ApiDefinitionService;
import com.hfwas.devops.apitest.apidefine.service.ApiGroupService;
import com.hfwas.devops.apitest.apidefine.vo.ApiDefinitionDetailVO;
import com.hfwas.devops.apitest.apidefine.vo.ApiGroupVO;
import com.hfwas.devops.apitest.support.TestCurrentUserAccessor;
import com.hfwas.devops.apitest.support.TestProjectAccessValidator;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 测试模块入口级项目隔离测试（MockMvc）。
 *
 * @author hfwas
 */
@DisplayName("ApiTestIsolation — HTTP 入口项目隔离")
class ApiTestIsolationTest extends BaseApiTest {

    @Autowired
    private ApiDefinitionService apiDefinitionService;

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestProjectAccessValidator projectAccessValidator;

    @Autowired
    private TestCurrentUserAccessor currentUserAccessor;

    @AfterEach
    void resetAccessContext() {
        projectAccessValidator.reset();
        currentUserAccessor.reset();
    }

    @Test
    @DisplayName("page 不带 projectId 只返回可见项目，空集返回空页")
    void listScopedToAccessibleProjects() {
        apiDefinitionService.create(buildCreateDTO(1001L, "/api/own", "自己的接口"));
        apiDefinitionService.create(buildCreateDTO(2002L, "/api/foreign", "外部接口"));

        projectAccessValidator.allow(testProjectId());
        ApiDefinitionQueryDTO query = new ApiDefinitionQueryDTO();
        IPage<?> page = apiDefinitionService.pageQuery(query);
        assertEquals(1, page.getTotal());

        projectAccessValidator.denyAll();
        assertEquals(0, apiDefinitionService.pageQuery(new ApiDefinitionQueryDTO()).getTotal());
    }

    @Test
    @DisplayName("旧客户端多传 userId 被忽略，身份取当前登录用户")
    void legacyUserIdParamIgnored() throws Exception {
        currentUserAccessor.setUser(77L, 1L, "admin");

        String body = toJson(Map.of(
                "projectId", 1001,
                "name", "上下文身份接口",
                "path", "/api/context-user",
                "method", "GET"));

        String response = mockMvc.perform(post("/apitest/definitions?userId=999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("data").get("id").asLong();
        assertEquals(77L, apiDefinitionMapper.selectById(id).getCreateBy());
    }

    @Test
    @DisplayName("跨项目 GET 定义/分组返回 HTTP 403 + 20002")
    void crossProjectReturns403() throws Exception {
        ApiDefinitionDetailVO foreignDefinition =
                apiDefinitionService.create(buildCreateDTO(2002L, "/api/foreign", "外部接口"));
        ApiGroupVO foreignGroup = apiGroupService.create(buildGroupDTO(2002L, "外部分组"));

        projectAccessValidator.allow(testProjectId());

        mockMvc.perform(get("/apitest/definitions/{id}", foreignDefinition.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20002));

        mockMvc.perform(get("/apitest/groups/{id}", foreignGroup.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20002));
    }

    private ApiDefinitionCreateDTO buildCreateDTO(Long projectId, String path, String name) {
        ApiDefinitionCreateDTO dto = new ApiDefinitionCreateDTO();
        dto.setProjectId(projectId);
        dto.setName(name);
        dto.setPath(path);
        dto.setMethod("GET");
        return dto;
    }

    private ApiGroupCreateDTO buildGroupDTO(Long projectId, String name) {
        ApiGroupCreateDTO dto = new ApiGroupCreateDTO();
        dto.setProjectId(projectId);
        dto.setName(name);
        return dto;
    }
}
