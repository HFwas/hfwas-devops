package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.NamespaceVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/container/clusters/{clusterId}/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

    private final ResourceService resourceService;

    @GetMapping
    public BaseResult<IPage<NamespaceVO>> listNamespaces(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long tenantId = SecurityHelper.requireTenantId();
        return BaseResult.ok(resourceService.listNamespaces(clusterId, keyword, pageNo, pageSize, tenantId));
    }
}