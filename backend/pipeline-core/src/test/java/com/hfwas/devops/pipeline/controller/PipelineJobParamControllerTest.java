package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.JobParamDefinitionVO;
import com.hfwas.devops.pipeline.dto.JobParamPreviewResultVO;
import com.hfwas.devops.pipeline.service.PipelineJobParamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-027、032、033、050、910。
 */
@ExtendWith(MockitoExtension.class)
class PipelineJobParamControllerTest {

    @Mock
    private PipelineJobParamService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PipelineJobParamController(service))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-033: 按流水线列出参数")
    void listReturnsParams() throws Exception {
        JobParamDefinitionVO vo = new JobParamDefinitionVO();
        vo.setParamKey("PROFILE");
        when(service.listByPipeline(1L)).thenReturn(List.of(vo));

        mvc.perform(get("/pipeline/job-params").param("pipelineId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paramKey").value("PROFILE"));
    }

    @Test
    @DisplayName("PIPE-032: 参数详情")
    void getReturnsParam() throws Exception {
        JobParamDefinitionVO vo = new JobParamDefinitionVO();
        vo.setId(5L);
        when(service.getById(5L)).thenReturn(vo);

        mvc.perform(get("/pipeline/job-params/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("PIPE-032: 创建参数返回 id")
    void createReturnsId() throws Exception {
        when(service.create(any())).thenReturn(8L);

        mvc.perform(post("/pipeline/job-params")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pipelineId\":1,\"jobId\":2,\"paramKey\":\"PROFILE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(8));
    }

    @Test
    @DisplayName("PIPE-032: 更新参数")
    void updateReturnsOk() throws Exception {
        mvc.perform(put("/pipeline/job-params/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pipelineId\":1,\"jobId\":2,\"paramKey\":\"PROFILE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).update(eq(5L), any());
    }

    @Test
    @DisplayName("PIPE-032: 删除参数")
    void deleteReturnsOk() throws Exception {
        mvc.perform(delete("/pipeline/job-params/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).delete(5L);
    }

    @Test
    @DisplayName("PIPE-050: 远程选项预览")
    void previewApiReturnsOptions() throws Exception {
        JobParamPreviewResultVO result = new JobParamPreviewResultVO();
        result.setSuccess(true);
        result.setOptions(List.of("a", "b"));
        when(service.previewApi(any(), any(), any(), any())).thenReturn(result);

        mvc.perform(post("/pipeline/job-params/preview-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiUrl\":\"http://x\",\"apiMethod\":\"GET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.options[0]").value("a"));
    }

    @Test
    @DisplayName("PIPE-032: 不存在返回 10003")
    void getMissingReturnsNotFound() throws Exception {
        when(service.getById(5L))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "参数定义不存在"));

        mvc.perform(get("/pipeline/job-params/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }
}
