package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.argo.*;
import com.hfwas.devops.container.service.argo.ArgoWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Argo Workflows CRD 代理 Controller。
 * 前端 /api/argo/... → Kong (/api strip) → /argo/... → 本 Controller
 * 通过 Fabric8 K8s 客户端直连 argoproj.io/v1alpha1 CRD。
 */
@RestController
@RequestMapping("/argo")
@RequiredArgsConstructor
public class ArgoWorkflowController {

    private final ArgoWorkflowService argoWorkflowService;

    // ==================== Workflows ====================

    @PostMapping("/workflows/page")
    public BaseResult<IPage<WorkflowSummaryVO>> pageWorkflows(@RequestBody Map<String, Object> params) {
        int pageNo = params.get("pageNo") != null ? Integer.parseInt(params.get("pageNo").toString()) : 1;
        int pageSize = params.get("pageSize") != null ? Integer.parseInt(params.get("pageSize").toString()) : 20;
        String keyword = params.get("keyword") != null ? params.get("keyword").toString() : null;
        String status = params.get("status") != null ? params.get("status").toString() : null;
        return BaseResult.ok(argoWorkflowService.pageWorkflows(pageNo, pageSize, keyword, status));
    }

    @GetMapping("/workflows/{name}")
    public BaseResult<WorkflowDetailVO> getWorkflow(@PathVariable String name) {
        return BaseResult.ok(argoWorkflowService.getWorkflow(name));
    }

    @PostMapping("/workflows")
    public BaseResult<Void> createWorkflow(@RequestBody JsonNode spec) {
        argoWorkflowService.createWorkflow(spec);
        return BaseResult.ok();
    }

    @DeleteMapping("/workflows/{name}")
    public BaseResult<Void> deleteWorkflow(@PathVariable String name) {
        argoWorkflowService.deleteWorkflow(name);
        return BaseResult.ok();
    }

    @PostMapping("/workflows/{name}/submit")
    public BaseResult<Map<String, String>> submitWorkflow(@PathVariable String name) {
        String runId = argoWorkflowService.submitWorkflow(name);
        return BaseResult.ok(Map.of("runId", runId));
    }

    // ==================== Runs ====================

    @GetMapping("/workflows/{name}/runs")
    public BaseResult<IPage<WorkflowRunVO>> pageRuns(
            @PathVariable String name,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return BaseResult.ok(argoWorkflowService.pageRuns(name, pageNo, pageSize));
    }

    @GetMapping("/workflows/runs/{runId}")
    public BaseResult<WorkflowRunVO> getRun(@PathVariable String runId) {
        return BaseResult.ok(argoWorkflowService.getRun(runId));
    }

    @GetMapping("/workflows/runs/{runId}/logs")
    public BaseResult<List<WorkflowRunLogVO>> getRunLogs(
            @PathVariable String runId,
            @RequestParam(required = false) String nodeId) {
        return BaseResult.ok(argoWorkflowService.getRunLogs(runId, nodeId));
    }

    @GetMapping("/workflows/runs/{runId}/events")
    public BaseResult<List<WorkflowEventVO>> getRunEvents(@PathVariable String runId) {
        return BaseResult.ok(argoWorkflowService.getRunEvents(runId));
    }

    // ==================== Templates ====================

    @PostMapping("/templates/page")
    public BaseResult<IPage<WorkflowTemplateVO>> pageTemplates(@RequestBody Map<String, Object> params) {
        int pageNo = params.get("pageNo") != null ? Integer.parseInt(params.get("pageNo").toString()) : 1;
        int pageSize = params.get("pageSize") != null ? Integer.parseInt(params.get("pageSize").toString()) : 20;
        String keyword = params.get("keyword") != null ? params.get("keyword").toString() : null;
        return BaseResult.ok(argoWorkflowService.pageTemplates(pageNo, pageSize, keyword));
    }

    @GetMapping("/templates/{name}")
    public BaseResult<WorkflowTemplateVO> getTemplate(@PathVariable String name) {
        return BaseResult.ok(argoWorkflowService.getTemplate(name));
    }

    @PostMapping("/templates")
    public BaseResult<Void> saveTemplate(@RequestBody JsonNode spec) {
        argoWorkflowService.saveTemplate(spec);
        return BaseResult.ok();
    }
}