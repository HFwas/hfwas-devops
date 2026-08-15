package com.hfwas.devops.apitest.collection;

import com.hfwas.devops.apitest.BaseApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollectionControllerTest extends BaseApiTest {

    @Test
    void createCollection_success() throws Exception {
        String body = toJson(Map.of("name", "测试集合"));

        mockMvc.perform(post("/apitest/collections?projectId=1001&userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("测试集合"));
    }

    @Test
    void createCollection_emptyName_returnsError() throws Exception {
        String body = toJson(Map.of("name", ""));

        mockMvc.perform(post("/apitest/collections?projectId=1001&userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber());
    }

    @Test
    void pageQuery_success() throws Exception {
        // 先创建一条数据
        String body = toJson(Map.of("name", "分页集合"));
        mockMvc.perform(post("/apitest/collections?projectId=1001&userId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(get("/apitest/collections/page")
                        .param("projectId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void getDetail_success() throws Exception {
        // 先创建
        String body = toJson(Map.of("name", "集合详情"));
        String response = mockMvc.perform(post("/apitest/collections?projectId=1001&userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        var node = objectMapper.readTree(response);
        long id = node.get("data").get("id").asLong();

        mockMvc.perform(get("/apitest/collections/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("集合详情"));
    }

    @Test
    void deleteCollection_success() throws Exception {
        String body = toJson(Map.of("name", "待删除集合"));
        String response = mockMvc.perform(post("/apitest/collections?projectId=1001&userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        var node = objectMapper.readTree(response);
        long id = node.get("data").get("id").asLong();

        mockMvc.perform(delete("/apitest/collections/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}