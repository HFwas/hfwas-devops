package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.PodDetailVO;
import com.hfwas.devops.container.dto.PodSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class PodController {

    private final ResourceService resourceService;

    @GetMapping("/pods")
    public BaseResult<IPage<PodSummaryVO>> listPods(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listPods(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}")
    public BaseResult<PodDetailVO> getPod(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getPod(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/yaml")
    public BaseResult<String> getPodYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getPodYaml(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/pods/{name}/logs")
    public BaseResult<String> getPodLogs(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false) String container,
            @RequestParam(required = false) Integer tailLines) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getPodLogs(clusterId, namespace, name, container, tailLines, tenantId));
    }

    @DeleteMapping("/namespaces/{namespace}/pods/{name}")
    public BaseResult<Void> deletePod(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.deletePod(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }
}