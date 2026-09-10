package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.ServiceDetailVO;
import com.hfwas.devops.container.dto.ServiceSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class ServiceController {

    private final ResourceService resourceService;

    @GetMapping("/services")
    public BaseResult<IPage<ServiceSummaryVO>> listServices(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listServices(clusterId, namespace, keyword, pageNo, pageSize, tenantId));
    }

    @GetMapping("/namespaces/{namespace}/services/{name}")
    public BaseResult<ServiceDetailVO> getService(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getService(clusterId, namespace, name, tenantId));
    }
}