package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.StatefulSetSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class StatefulSetController {

    private final ResourceService resourceService;

    @GetMapping("/statefulsets")
    public BaseResult<IPage<StatefulSetSummaryVO>> listStatefulSets(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listStatefulSets(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/statefulsets/{name}/yaml")
    public BaseResult<String> getStatefulSetYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getStatefulSetYaml(clusterId, namespace, name, tenantId));
    }

    @PutMapping("/namespaces/{namespace}/statefulsets/{name}/yaml")
    public BaseResult<Void> updateStatefulSetYaml(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody String yamlBody) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.updateStatefulSetYaml(clusterId, namespace, name, yamlBody, tenantId);
        return BaseResult.ok();
    }

    @DeleteMapping("/namespaces/{namespace}/statefulsets/{name}")
    public BaseResult<Void> deleteStatefulSet(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.deleteStatefulSet(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }
}