package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.dto.PipelineVO;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIPE-123..126：容器查询、归属校验与无集群降级。
 */
@ExtendWith(MockitoExtension.class)
class PodExecControllerTest {

    @Mock
    private PipelineDefinitionService definitionService;
    @Mock
    private PipelineRunMapper runMapper;
    @Mock
    private PipelineRunJobMapper runJobMapper;
    @Mock
    private ObjectProvider<KubernetesClient> kubernetesClients;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PodExecController(definitionService, runMapper, runJobMapper, kubernetesClients))
                .setControllerAdvice(new PipelineTestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("PIPE-125: 未配置集群时使用 DB 容器名并标记 unavailable")
    void containersWithoutClusterFallbackToDb() throws Exception {
        when(definitionService.get(1L)).thenReturn(new PipelineVO());
        when(runMapper.selectById(99L)).thenReturn(run());
        when(runJobMapper.selectById(21L)).thenReturn(job("[\"step-build\"]"));
        when(kubernetesClients.getIfAvailable()).thenReturn(null);

        mvc.perform(get("/pipeline/pipelines/1/runs/99/jobs/21/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.podExists").value("unknown"))
                .andExpect(jsonPath("$.data.containers[0].name").value("step-build"))
                .andExpect(jsonPath("$.data.containers[0].state").value("unknown"))
                .andExpect(jsonPath("$.data.containers[0].recommendedMode").value("unavailable"))
                .andExpect(jsonPath("$.data.defaultContainer").value("step-build"));
    }

    @Test
    @DisplayName("PIPE-126: run 不属于 pipeline 时返回空结果")
    void runMismatchReturnsEmpty() throws Exception {
        when(definitionService.get(1L)).thenReturn(new PipelineVO());
        PipelineRunEntity run = run();
        run.setPipelineId(2L);
        when(runMapper.selectById(99L)).thenReturn(run);

        mvc.perform(get("/pipeline/pipelines/1/runs/99/jobs/21/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.podExists").value("false"))
                .andExpect(jsonPath("$.data.containers").isEmpty());
    }

    @Test
    @DisplayName("PIPE-126: job 不属于 run 时返回空结果")
    void jobMismatchReturnsEmpty() throws Exception {
        when(definitionService.get(1L)).thenReturn(new PipelineVO());
        when(runMapper.selectById(99L)).thenReturn(run());
        PipelineRunJobEntity job = job("[\"step-build\"]");
        job.setRunId(100L);
        when(runJobMapper.selectById(21L)).thenReturn(job);

        mvc.perform(get("/pipeline/pipelines/1/runs/99/jobs/21/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.podExists").value("false"));
    }

    @Test
    @DisplayName("PIPE-126: pipeline 不属于当前租户返回 10003")
    void pipelineOwnershipFailureReturnsBizCode() throws Exception {
        when(definitionService.get(1L))
                .thenThrow(BizException.of(ResultCode.NOT_FOUND, "流水线不存在"));

        mvc.perform(get("/pipeline/pipelines/1/runs/99/jobs/21/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("PIPE-124: Pod 未调度时 podExists=false")
    void podNotScheduledReturnsFalse() throws Exception {
        when(definitionService.get(1L)).thenReturn(new PipelineVO());
        when(runMapper.selectById(99L)).thenReturn(run());
        PipelineRunJobEntity job = job("[\"step-build\"]");
        job.setPodName(null);
        when(runJobMapper.selectById(21L)).thenReturn(job);
        when(kubernetesClients.getIfAvailable()).thenReturn(null);

        mvc.perform(get("/pipeline/pipelines/1/runs/99/jobs/21/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.podExists").value("unknown"))
                .andExpect(jsonPath("$.data.containers[0].state").value("unknown"));
    }

    private static PipelineRunEntity run() {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(99L);
        run.setPipelineId(1L);
        run.setTenantId(9L);
        run.setTektonName("demo-run");
        return run;
    }

    private static PipelineRunJobEntity job(String containers) {
        PipelineRunJobEntity job = new PipelineRunJobEntity();
        job.setId(21L);
        job.setRunId(99L);
        job.setNamespace("hfwas-pipeline");
        job.setPodName("demo-pod");
        job.setContainers(containers);
        return job;
    }
}
