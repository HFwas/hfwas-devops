package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineRunArtifactVO;
import com.hfwas.devops.pipeline.entity.PipelineRunArtifactEntity;
import com.hfwas.devops.pipeline.service.PipelineRunArtifactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-103..111、157。
 */
@ExtendWith(MockitoExtension.class)
class PipelineRunArtifactControllerTest {

    @Mock
    private PipelineRunArtifactService artifactService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PipelineRunArtifactController(artifactService))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-103: 上传产物")
    void uploadReturnsArtifact() throws Exception {
        PipelineRunArtifactVO vo = new PipelineRunArtifactVO();
        vo.setId(5L);
        vo.setFileName("sbom.json");
        when(artifactService.upload(eq(99L), eq(21L), eq("sbom"), any())).thenReturn(vo);
        MockMultipartFile file = new MockMultipartFile("file", "sbom.json", "application/json", "{}".getBytes());

        mvc.perform(multipart("/pipeline/runs/99/artifacts")
                        .file(file)
                        .param("jobId", "21")
                        .param("type", "sbom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.fileName").value("sbom.json"));
    }

    @Test
    @DisplayName("PIPE-105: run 不存在返回 10003")
    void uploadMissingRunReturnsNotFound() throws Exception {
        when(artifactService.upload(eq(99L), any(), any(), any()))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "运行记录不存在"));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});

        mvc.perform(multipart("/pipeline/runs/99/artifacts").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("PIPE-109: 产物列表")
    void listReturnsArtifacts() throws Exception {
        PipelineRunArtifactVO vo = new PipelineRunArtifactVO();
        vo.setId(5L);
        when(artifactService.listByRun(99L)).thenReturn(List.of(vo));

        mvc.perform(get("/pipeline/runs/99/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5));
    }

    @Test
    @DisplayName("PIPE-109: 下载产物返回附件流")
    void downloadReturnsStream() throws Exception {
        PipelineRunArtifactEntity entity = new PipelineRunArtifactEntity();
        entity.setId(5L);
        entity.setFileName("report.txt");
        entity.setContentType("text/plain");
        entity.setFileSize(5L);
        entity.setStoragePath("99/abc/report.txt");
        when(artifactService.getById(5L)).thenReturn(entity);
        when(artifactService.openStream(entity))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/pipeline/runs/99/artifacts/5/download"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    @Test
    @DisplayName("PIPE-110: 查询最新 SBOM")
    void sbomReturnsLatest() throws Exception {
        PipelineRunArtifactEntity entity = new PipelineRunArtifactEntity();
        entity.setId(5L);
        entity.setRunId(99L);
        entity.setArtifactType("sbom");
        entity.setFileName("sbom.json");
        when(artifactService.getSbomByRun(99L)).thenReturn(entity);

        mvc.perform(get("/pipeline/runs/99/artifacts/sbom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.fileName").value("sbom.json"));
    }

    @Test
    @DisplayName("PIPE-109: 产物不存在返回 10003")
    void downloadMissingReturnsNotFound() throws Exception {
        when(artifactService.getById(5L))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "产物不存在"));

        mvc.perform(get("/pipeline/runs/99/artifacts/5/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }
}
