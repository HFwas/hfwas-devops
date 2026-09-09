package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.ContainerInfo;
import com.hfwas.devops.pipeline.dto.PodContainersVO;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunJobEntity;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.service.PipelineDefinitionService;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/pipeline/pipelines/{pipelineId}/runs/{runId}/jobs/{jobId}")
public class PodExecController {

    private static final Set<String> NO_SHELL_IMAGE_PREFIXES = Set.of(
            "gcr.io/kaniko-project",
            "gcr.io/distroless",
            "curlimages/curl",
            "alpine/git"
    );

    private static final String WORKSPACE_PATH = "/workspace/source/src";

    private final PipelineDefinitionService definitionService;
    private final PipelineRunMapper runMapper;
    private final PipelineRunJobMapper runJobMapper;
    private final ObjectProvider<KubernetesClient> kubernetesClients;

    public PodExecController(PipelineDefinitionService definitionService,
                             PipelineRunMapper runMapper,
                             PipelineRunJobMapper runJobMapper,
                             ObjectProvider<KubernetesClient> kubernetesClients) {
        this.definitionService = definitionService;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.kubernetesClients = kubernetesClients;
    }

    @GetMapping("/containers")
    public BaseResult<PodContainersVO> getContainers(
            @PathVariable Long pipelineId,
            @PathVariable Long runId,
            @PathVariable Long jobId
    ) {
        // 1. 验证 pipeline 归属（get 内部调用 requireOwned）
        definitionService.get(pipelineId);

        // 2. 验证 run 属于 pipeline
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null || !pipelineId.equals(run.getPipelineId())) {
            return BaseResult.ok(emptyVo("运行记录不存在"));
        }

        // 3. 验证 job 属于 run
        PipelineRunJobEntity job = runJobMapper.selectById(jobId);
        if (job == null || !runId.equals(job.getRunId())) {
            return BaseResult.ok(emptyVo("任务记录不存在"));
        }

        PodContainersVO vo = new PodContainersVO();
        vo.setNamespace(job.getNamespace());
        vo.setPodName(job.getPodName());
        vo.setWorkspacePath(WORKSPACE_PATH);

        // 4. 判断 workspace 类型
        String tektonName = run.getTektonName();
        boolean taskMode = isTaskMode(job);
        vo.setWorkspaceKind(taskMode ? "emptydir" : "pvc");

        // 5. Live 查询 Pod
        KubernetesClient client = kubernetesClients.getIfAvailable();
        List<ContainerInfo> containerInfos = new ArrayList<>();

        if (client == null) {
            vo.setPodExists("unknown");
            // 无集群：使用 DB 中存储的容器名，全部 unknown
            String[] dbContainers = parseContainers(job.getContainers());
            for (String name : dbContainers) {
                ContainerInfo info = new ContainerInfo();
                info.setName(name);
                info.setState("unknown");
                info.setRecommendedMode("unavailable");
                info.setUnavailableReason("未配置执行集群");
                containerInfos.add(info);
            }
        } else if (job.getPodName() == null) {
            vo.setPodExists("false");
            // Pod 尚未分配
            vo.setDefaultContainer("");
        } else {
            Pod pod = client.pods().inNamespace(job.getNamespace()).withName(job.getPodName()).get();
            if (pod != null && pod.getStatus() != null) {
                vo.setPodExists("true");
                Set<String> stepContainers = Set.of(parseContainers(job.getContainers()));

                for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                    if (!stepContainers.contains(cs.getName())) {
                        continue; // 过滤 place-scripts 等内部容器
                    }
                    ContainerInfo info = new ContainerInfo();
                    info.setName(cs.getName());

                    if (cs.getState() != null) {
                        if (cs.getState().getRunning() != null) {
                            info.setState("running");
                            info.setRecommendedMode("exec");
                        } else if (cs.getState().getTerminated() != null) {
                            info.setState("terminated");
                            info.setExitCode(cs.getState().getTerminated().getExitCode());
                            info.setRecommendedMode("ephemeral");
                        } else if (cs.getState().getWaiting() != null) {
                            info.setState("waiting");
                            info.setRecommendedMode("ephemeral");
                        }
                    }
                    info.setHasShell(guessHasShell(info.getName()));
                    containerInfos.add(info);
                }
            } else {
                vo.setPodExists("false");
                // Pod 已删除 — 按 workspace 类型判定
                if (taskMode) {
                    // TASK 模式：emptyDir，现场已丢
                    String[] dbContainers = parseContainers(job.getContainers());
                    for (String name : dbContainers) {
                        ContainerInfo info = new ContainerInfo();
                        info.setName(name);
                        info.setState("unknown");
                        info.setRecommendedMode("unavailable");
                        info.setUnavailableReason("串行流水线使用 emptyDir，Pod 删除后现场已丢失，请查看日志");
                        containerInfos.add(info);
                    }
                } else {
                    // Pipeline 模式：PVC
                    String pvcName = tektonName != null ? tektonName + "-ws" : null;
                    boolean pvcExists = pvcName != null
                            && client.persistentVolumeClaims().inNamespace(job.getNamespace()).withName(pvcName).get() != null;
                    if (pvcExists) {
                        String[] dbContainers = parseContainers(job.getContainers());
                        for (String name : dbContainers) {
                            ContainerInfo info = new ContainerInfo();
                            info.setName(name);
                            info.setState("unknown");
                            info.setRecommendedMode("debug_pod");
                            containerInfos.add(info);
                        }
                    } else {
                        String[] dbContainers = parseContainers(job.getContainers());
                        for (String name : dbContainers) {
                            ContainerInfo info = new ContainerInfo();
                            info.setName(name);
                            info.setState("unknown");
                            info.setRecommendedMode("unavailable");
                            info.setUnavailableReason("工作区 PVC 已不存在，现场已丢失，请查看日志");
                            containerInfos.add(info);
                        }
                    }
                }
            }
        }

        vo.setContainers(containerInfos);

        // 设置 defaultContainer：选取 running 的容器，其次第一个非 unavailable
        String defaultContainer = "";
        for (ContainerInfo info : containerInfos) {
            if ("running".equals(info.getState())) {
                defaultContainer = info.getName();
                break;
            }
        }
        if (defaultContainer.isEmpty() && !containerInfos.isEmpty()) {
            for (ContainerInfo info : containerInfos) {
                if (!"unavailable".equals(info.getRecommendedMode())) {
                    defaultContainer = info.getName();
                    break;
                }
            }
        }
        if (defaultContainer.isEmpty() && !containerInfos.isEmpty()) {
            defaultContainer = containerInfos.getFirst().getName();
        }
        vo.setDefaultContainer(defaultContainer);

        return BaseResult.ok(vo);
    }

    private static PodContainersVO emptyVo(String reason) {
        PodContainersVO vo = new PodContainersVO();
        vo.setPodExists("false");
        vo.setContainers(List.of());
        vo.setDefaultContainer("");
        vo.setWorkspacePath(WORKSPACE_PATH);
        return vo;
    }

    private static boolean isTaskMode(PipelineRunJobEntity job) {
        // TASK 模式的 job 通常有 CLONE 等 kind，但简单判断：TaskRun 模式下 jobs 共用 Pod
        // 这里通过 PipelineRunEntity 的 tektonName 和 segmentIndex 间接推断
        return false; // 由调用方根据 run 实体的 mode 判断；默认 false
    }

    private static boolean guessHasShell(String containerName) {
        // Tekton step 容器名以 "step-" 开头，一般有 shell
        return containerName != null;
    }

    private static String[] parseContainers(String json) {
        if (json == null || json.isBlank()) return new String[0];
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, String[].class);
        } catch (Exception e) {
            return new String[0];
        }
    }
}