package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.dto.TaskKindVersionVO;
import com.hfwas.devops.pipeline.service.PipelineTaskKindService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-077..102：任务市场/任务类型接口。
 */
@ExtendWith(MockitoExtension.class)
class PipelineTaskKindControllerTest {

    @Mock
    private PipelineTaskKindService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PipelineTaskKindController(service))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-077: 列表返回启用状态与版本")
    void listReturnsKinds() throws Exception {
        when(service.listAll()).thenReturn(List.of(vo("BUILD", true)));

        mvc.perform(get("/pipeline/task-kinds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kindValue").value("BUILD"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    @DisplayName("PIPE-078: 详情不存在返回 10003")
    void getMissingReturnsNotFound() throws Exception {
        when(service.getByKind("NOPE"))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "Task 类型不存在: NOPE"));

        mvc.perform(get("/pipeline/task-kinds/NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("PIPE-079: 更新概览")
    void updateReturnsOk() throws Exception {
        mvc.perform(put("/pipeline/task-kinds/BUILD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"构建\",\"taskGroup\":\"构建\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).update(eq("BUILD"), any());
    }

    @Test
    @DisplayName("PIPE-080: label 为空触发参数校验 400")
    void updateRejectsBlankLabel() throws Exception {
        mvc.perform(put("/pipeline/task-kinds/BUILD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PIPE-098: 启停任务类型")
    void toggleReturnsOk() throws Exception {
        mvc.perform(patch("/pipeline/task-kinds/BUILD/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).toggle("BUILD");
    }

    @Test
    @DisplayName("PIPE-096: 版本列表")
    void listVersionsReturnsRows() throws Exception {
        TaskKindVersionVO version = new TaskKindVersionVO();
        version.setVersion("1.0.0");
        version.setIsCurrent(true);
        when(service.listVersions("BUILD")).thenReturn(List.of(version));

        mvc.perform(get("/pipeline/task-kinds/BUILD/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isCurrent").value(true));
    }

    @Test
    @DisplayName("PIPE-091: 发布版本")
    void publishReturnsVersion() throws Exception {
        TaskKindVersionVO version = new TaskKindVersionVO();
        version.setVersion("1.0.1");
        when(service.publish(eq("BUILD"), any())).thenReturn(version);

        mvc.perform(post("/pipeline/task-kinds/BUILD/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("1.0.1"));
    }

    @Test
    @DisplayName("PIPE-097: 激活版本")
    void activateReturnsKind() throws Exception {
        when(service.activate("BUILD", "1.0.0")).thenReturn(vo("BUILD", true));

        mvc.perform(put("/pipeline/task-kinds/BUILD/versions/1.0.0/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kindValue").value("BUILD"));
    }

    @Test
    @DisplayName("PIPE-100: 空脚本直接通过")
    void validateEmptyScriptReturnsValid() throws Exception {
        mvc.perform(post("/pipeline/task-kinds/BUILD/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("PIPE-099: 合法脚本通过 bash -n")
    void validateValidScript() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(commandExists("bash"));

        mvc.perform(post("/pipeline/task-kinds/BUILD/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\":\"echo hello\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("PIPE-099: 非法脚本返回 valid=false")
    void validateInvalidScript() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(commandExists("bash"));

        mvc.perform(post("/pipeline/task-kinds/BUILD/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"script\":\"if then\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    private static boolean commandExists(String command) {
        try {
            return new ProcessBuilder(command, "-c", "true").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static TaskKindVO vo(String kind, boolean enabled) {
        TaskKindVO vo = new TaskKindVO();
        vo.setKindValue(kind);
        vo.setLabel("构建");
        vo.setEnabled(enabled);
        vo.setParams(List.of());
        return vo;
    }
}
