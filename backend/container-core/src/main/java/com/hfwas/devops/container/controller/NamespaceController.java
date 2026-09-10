package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.NamespaceVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/container/clusters/{clusterId}/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

    private final ResourceService resourceService;

    @GetMapping
    public BaseResult<List<NamespaceVO>> listNamespaces(@PathVariable Long clusterId) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listNamespaces(clusterId, tenantId));
    }
}