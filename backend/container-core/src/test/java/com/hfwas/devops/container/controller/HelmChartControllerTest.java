package com.hfwas.devops.container.controller;

import com.hfwas.devops.container.dto.HelmChartArtifactVO;
import com.hfwas.devops.container.error.HelmChartConflictException;
import com.hfwas.devops.container.service.helm.HelmChartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HelmChartControllerTest {

    @Mock
    private HelmChartService helmChartService;

    @Test
    void uploadConflictIsHttp409() throws Exception {
        when(helmChartService.upload(any(), isNull())).thenThrow(new HelmChartConflictException("相同 Chart 名称与版本已存在"));
        MockMvc mvc = mvc();
        MockMultipartFile file = new MockMultipartFile("file", "sample-0.1.0.tgz", "application/gzip", new byte[]{1, 2, 3});

        mvc.perform(multipart("/container/helm/charts/upload").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30303));
    }

    @Test
    void uploadReturnsArtifact() throws Exception {
        HelmChartArtifactVO vo = new HelmChartArtifactVO();
        vo.setId(9L);
        vo.setChartName("sample");
        vo.setVersion("0.1.0");
        vo.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        when(helmChartService.upload(any(), eq(4L))).thenReturn(vo);
        MockMvc mvc = mvc();
        MockMultipartFile file = new MockMultipartFile("file", "sample-0.1.0.tgz", "application/gzip", new byte[]{1, 2, 3});

        mvc.perform(multipart("/container/helm/charts/upload")
                        .file(file)
                        .param("repositoryId", "4")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chartRef").value("oci://harbor.example/charts/sample:0.1.0"));
    }

    @Test
    void getVersionKeepsDots() throws Exception {
        HelmChartArtifactVO vo = new HelmChartArtifactVO();
        vo.setChartName("sample");
        vo.setVersion("0.1.0");
        when(helmChartService.getVersion("sample", "0.1.0", null)).thenReturn(vo);
        when(helmChartService.listVersions("sample", null)).thenReturn(List.of(vo));

        MockMvc mvc = mvc();
        mvc.perform(get("/container/helm/charts/sample/versions/0.1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("0.1.0"));
        mvc.perform(get("/container/helm/charts/sample/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].chartName").value("sample"));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new HelmChartController(helmChartService))
                .setControllerAdvice(new HelmChartExceptionHandler())
                .build();
    }
}
