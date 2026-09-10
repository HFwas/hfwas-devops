package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.ConfigMapSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class ConfigMapController {

    private final ResourceService resourceService;

    @GetMapping("/configmaps")
    public BaseResult<IPage<ConfigMapSummaryVO>> listConfigMaps(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listConfigMaps(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/configmaps/{name}/yaml")
    public BaseResult<String> getConfigMapYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getConfigMapYaml(clusterId, namespace, name, tenantId));
    }

    @PutMapping("/namespaces/{namespace}/configmaps/{name}/yaml")
    public BaseResult<Void> updateConfigMapYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody String yamlBody) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.updateConfigMapYaml(clusterId, namespace, name, yamlBody, tenantId);
        return BaseResult.ok();
    }

    @DeleteMapping("/namespaces/{namespace}/configmaps/{name}")
    public BaseResult<Void> deleteConfigMap(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.deleteConfigMap(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }
}