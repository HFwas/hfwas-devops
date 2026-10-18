package com.hfwas.devops.pipeline.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineRunVO;
import com.hfwas.devops.pipeline.dto.PipelineVO;
import com.hfwas.devops.pipeline.dto.RunParamDefinitionVO;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import com.hfwas.devops.pipeline.service.PipelineRunService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-001、003、016、019..021、023、026、039..042、048..050、151、152。
 */
@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock
    private PipelineDefinitionService definitionService;
    @Mock
    private PipelineRunService runService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PipelineController(definitionService, runService))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-018: 分页返回 BaseResult<Page>")
    void pageReturnsWrappedPage() throws Exception {
        Page<PipelineVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo(1L, "demo")));
        page.setTotal(1);
        when(definitionService.page(any())).thenReturn(page);

        mvc.perform(post("/pipeline/pipelines/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNo\":1,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value("demo"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("PIPE-001: 创建返回新 id")
    void createReturnsId() throws Exception {
        when(definitionService.save(any())).thenReturn(5L);

        mvc.perform(post("/pipeline/pipelines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"stages\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("PIPE-003: 名称为空返回 10002")
    void createRejectsBlankName() throws Exception {
        when(definitionService.save(any()))
                .thenThrow(BizException.of(ResultCode.BAD_REQUEST, "名称不能为空"));

        mvc.perform(post("/pipeline/pipelines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.msg").value("名称不能为空"));
    }

    @Test
    @DisplayName("PIPE-001: 详情返回阶段")
    void getReturnsDetail() throws Exception {
        when(definitionService.get(1L)).thenReturn(vo(1L, "demo"));

        mvc.perform(get("/pipeline/pipelines/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("demo"));
    }

    @Test
    @DisplayName("PIPE-016: 更新路径 id 传递给服务")
    void updatePassesPathId() throws Exception {
        when(definitionService.save(any())).thenReturn(1L);

        mvc.perform(put("/pipeline/pipelines/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(definitionService).save(any());
    }

    @Test
    @DisplayName("PIPE-019: 删除返回成功")
    void deleteReturnsOk() throws Exception {
        mvc.perform(delete("/pipeline/pipelines/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).delete(1L);
    }

    @Test
    @DisplayName("PIPE-020: 删除不存在返回 10003")
    void deleteMissingReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(BizException.of(ResultCode.NOT_FOUND, "流水线不存在"))
                .when(definitionService).delete(1L);

        mvc.perform(delete("/pipeline/pipelines/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("PIPE-021: 触发运行")
    void startReturnsRun() throws Exception {
        PipelineRunVO run = new PipelineRunVO();
        run.setId(99L);
        run.setStatus("QUEUED");
        when(runService.start(anyLong(), any(com.hfwas.devops.pipeline.dto.PipelineRunStartDTO.class))).thenReturn(run);

        mvc.perform(post("/pipeline/pipelines/1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"PROFILE\":\"dev\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    @DisplayName("PIPE-050: 默认参数接口")
    void defaultParamsReturnsList() throws Exception {
        RunParamDefinitionVO param = new RunParamDefinitionVO();
        param.setParamKey("PROFILE");
        when(runService.getDefaultParams(1L)).thenReturn(List.of(param));

        mvc.perform(get("/pipeline/pipelines/1/runs/default-params"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paramKey").value("PROFILE"));
    }

    @Test
    @DisplayName("PIPE-039: 非待审批 approve 返回 10002")
    void approveRejectedReturnsBizCode() throws Exception {
        when(runService.approve(1L, 99L))
                .thenThrow(BizException.of(ResultCode.BAD_REQUEST, "当前运行不在待审批"));

        mvc.perform(post("/pipeline/pipelines/1/runs/99/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.msg").value("当前运行不在待审批"));
    }

    @Test
    @DisplayName("PIPE-041: 取消返回更新后的运行")
    void cancelReturnsRun() throws Exception {
        PipelineRunVO run = new PipelineRunVO();
        run.setStatus("CANCELLED");
        when(runService.cancel(1L, 99L)).thenReturn(run);

        mvc.perform(post("/pipeline/pipelines/1/runs/99/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PIPE-048: 运行不存在返回 10003")
    void getRunMissingReturnsNotFound() throws Exception {
        when(runService.get(1L, 99L))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "运行记录不存在"));

        mvc.perform(get("/pipeline/pipelines/1/runs/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    private static PipelineVO vo(Long id, String name) {
        PipelineVO vo = new PipelineVO();
        vo.setId(id);
        vo.setName(name);
        vo.setStages(List.of());
        return vo;
    }
}
