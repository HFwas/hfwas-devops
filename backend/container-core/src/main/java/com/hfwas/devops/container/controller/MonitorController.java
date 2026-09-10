package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.MonitorOverviewVO;
import com.hfwas.devops.container.dto.MonitorSeriesVO;
import com.hfwas.devops.container.service.SecurityHelper;
import com.hfwas.devops.container.service.prometheus.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/container/clusters/{clusterId}/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    /**
     * Cluster overview: aggregated resource usage.
     */
    @GetMapping("/overview")
    public BaseResult<MonitorOverviewVO> overview(@PathVariable Long clusterId) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryOverview(clusterId, tenantId));
    }

    // ── Node monitor ──

    @GetMapping("/nodes/{name}/cpu")
    public BaseResult<List<MonitorSeriesVO>> nodeCpu(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryNodeCpu(clusterId, name, tenantId));
    }

    @GetMapping("/nodes/{name}/memory")
    public BaseResult<List<MonitorSeriesVO>> nodeMemory(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryNodeMemory(clusterId, name, tenantId));
    }

    @GetMapping("/nodes/{name}/network")
    public BaseResult<List<MonitorSeriesVO>> nodeNetwork(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryNodeNetwork(clusterId, name, tenantId));
    }

    @GetMapping("/nodes/{name}/connections")
    public BaseResult<List<MonitorSeriesVO>> nodeConnections(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryNodeConnections(clusterId, name, tenantId));
    }

    @GetMapping("/nodes/{name}/disk")
    public BaseResult<List<MonitorSeriesVO>> nodeDisk(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryNodeDisk(clusterId, name, tenantId));
    }

    // ── Pod monitor ──

    @GetMapping("/namespaces/{namespace}/pods/{name}/cpu")
    public BaseResult<List<MonitorSeriesVO>> podCpu(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryPodCpu(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/memory")
    public BaseResult<List<MonitorSeriesVO>> podMemory(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryPodMemory(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/network")
    public BaseResult<List<MonitorSeriesVO>> podNetwork(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryPodNetwork(clusterId, namespace, name, tenantId));
    }

    // ── JVM monitor ──

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/check")
    public BaseResult<Map<String, Boolean>> jvmCheck(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        boolean hasJvm = monitorService.checkJvmMetrics(clusterId, namespace, name, tenantId);
        return BaseResult.ok(Map.of("hasJvmMetrics", hasJvm));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/heap")
    public BaseResult<List<MonitorSeriesVO>> jvmHeap(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryJvmHeap(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/nonheap")
    public BaseResult<List<MonitorSeriesVO>> jvmNonHeap(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryJvmNonHeap(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/gc")
    public BaseResult<List<MonitorSeriesVO>> jvmGc(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryJvmGc(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/thread")
    public BaseResult<List<MonitorSeriesVO>> jvmThread(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryJvmThread(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/jvm/memory-pools")
    public BaseResult<List<MonitorSeriesVO>> jvmMemoryPools(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(monitorService.queryJvmMemoryPools(clusterId, namespace, name, tenantId));
    }
}