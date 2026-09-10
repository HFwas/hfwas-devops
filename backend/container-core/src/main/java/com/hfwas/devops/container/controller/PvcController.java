package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.PvcSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class PvcController {

    private final ResourceService resourceService;

    @GetMapping("/pvcs")
    public BaseResult<IPage<PvcSummaryVO>> listPvcs(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listPersistentVolumeClaims(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @DeleteMapping("/namespaces/{namespace}/pvcs/{name}")
    public BaseResult<Void> deletePvc(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        resourceService.deletePersistentVolumeClaim(clusterId, namespace, name, tenantId);
        return BaseResult.ok();
    }
}