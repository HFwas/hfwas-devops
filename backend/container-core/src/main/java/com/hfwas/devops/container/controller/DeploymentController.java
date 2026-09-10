package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.DeploymentDetailVO;
import com.hfwas.devops.container.dto.DeploymentSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class DeploymentController {

    private final ResourceService resourceService;

    @GetMapping("/deployments")
    public BaseResult<IPage<DeploymentSummaryVO>> listDeployments(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listDeployments(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/deployments/{name}")
    public BaseResult<DeploymentDetailVO> getDeployment(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getDeployment(clusterId, namespace, name, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/deployments/{name}/yaml")
    public BaseResult<String> getDeploymentYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getDeploymentYaml(clusterId, namespace, name, tenantId));
    }

    @PutMapping("/namespaces/{namespace}/deployments/{name}/scale")
    public BaseResult<Void> scaleDeployment(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, Integer> body) {
        int replicas = body.getOrDefault("replicas", 1);
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.scaleDeployment(clusterId, namespace, name, replicas, tenantId);
        return BaseResult.ok();
    }

    @PutMapping("/namespaces/{namespace}/deployments/{name}/restart")
    public BaseResult<Void> restartDeployment(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.restartDeployment(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }

    @DeleteMapping("/namespaces/{namespace}/deployments/{name}")
    public BaseResult<Void> deleteDeployment(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.deleteDeployment(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }
}