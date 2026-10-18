package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.pipeline.dto.DependencyScanVO;
import com.hfwas.devops.pipeline.service.DependencyScanService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-112、118..120。
 */
@ExtendWith(MockitoExtension.class)
class DependencyScanControllerTest {

    @Mock
    private DependencyScanService scanService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DependencyScanController(scanService))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-112: 提交单次扫描")
    void submitReturnsScan() throws Exception {
        when(scanService.submitSingle(any())).thenReturn(scan(100L, 42L));

        mvc.perform(post("/dependency-scan/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/org/app.git\",\"stack\":\"JAVA_MAVEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pipelineId").value(42))
                .andExpect(jsonPath("$.data.runId").value(100));
    }

    @Test
    @DisplayName("PIPE-118: 批量提交")
    void batchSubmitReturnsList() throws Exception {
        when(scanService.submitBatch(anyList())).thenReturn(List.of(scan(100L, 42L), scan(101L, 43L)));

        mvc.perform(post("/dependency-scan/batch-submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scans\":[{\"repoUrl\":\"https://github.com/org/a.git\"},{\"repoUrl\":\"https://github.com/org/b.git\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("PIPE-120: 扫描任务列表")
    void listTasksReturnsRows() throws Exception {
        when(scanService.listRecent(20)).thenReturn(List.of(scan(100L, 42L)));

        mvc.perform(get("/dependency-scan/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pipelineId").value(42));
    }

    private static DependencyScanVO scan(Long runId, Long pipelineId) {
        DependencyScanVO vo = new DependencyScanVO();
        vo.setRunId(runId);
        vo.setPipelineId(pipelineId);
        vo.setStatus("QUEUED");
        return vo;
    }
}
