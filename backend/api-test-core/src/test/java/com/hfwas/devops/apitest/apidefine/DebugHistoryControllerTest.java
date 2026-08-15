package com.hfwas.devops.apitest.apidefine;

import com.hfwas.devops.apitest.BaseApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DebugHistoryControllerTest extends BaseApiTest {

    @Test
    void pageQuery_success() throws Exception {
        mockMvc.perform(get("/apitest/debug-histories/page")
                        .param("projectId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getDetail_notFound() throws Exception {
        mockMvc.perform(get("/apitest/debug-histories/{id}", 999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deleteBatch_success() throws Exception {
        mockMvc.perform(delete("/apitest/debug-histories/batch")
                        .param("ids", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}