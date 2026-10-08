package com.hfwas.devops.container.controller;

import com.hfwas.devops.container.dto.HelmReleaseVO;
import com.hfwas.devops.container.error.HelmReleaseConflictException;
import com.hfwas.devops.container.error.HelmReleaseNotFoundException;
import com.hfwas.devops.container.service.helm.HelmReleaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HelmReleaseControllerTest {

    @Mock
    private HelmReleaseService helmReleaseService;

    @Test
    void installConflictIsHttp409() throws Exception {
        when(helmReleaseService.install(eq(4L), eq("edge"), any()))
                .thenThrow(new HelmReleaseConflictException("cannot re-use a name"));
        MockMvc mvc = mvc();

        mvc.perform(post("/container/clusters/4/helm/releases/edge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"chartRef\":\"oci://harbor.example/charts/sample:0.1.0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30310));
    }

    @Test
    void missingReleaseIsHttp404() throws Exception {
        doThrow(new HelmReleaseNotFoundException("release: not found"))
                .when(helmReleaseService).uninstall(4L, "edge", "demo");
        MockMvc mvc = mvc();

        mvc.perform(delete("/container/clusters/4/helm/releases/edge/demo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30309));
    }

    @Test
    void rollbackKeepsTheBody() throws Exception {
        HelmReleaseVO vo = new HelmReleaseVO();
        vo.setName("demo");
        vo.setRevision(3);
        when(helmReleaseService.rollback(eq(4L), eq("edge"), eq("demo"), any())).thenReturn(vo);
        MockMvc mvc = mvc();

        mvc.perform(put("/container/clusters/4/helm/releases/edge/demo/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(3));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new HelmReleaseController(helmReleaseService))
                .setControllerAdvice(new HelmChartExceptionHandler())
                .build();
    }
}
