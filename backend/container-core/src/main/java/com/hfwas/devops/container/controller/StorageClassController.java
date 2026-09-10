package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.StorageClassSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class StorageClassController {

    private final ResourceService resourceService;

    @GetMapping("/storageclasses")
    public BaseResult<List<StorageClassSummaryVO>> listStorageClasses(
            @PathVariable Long clusterId) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listStorageClasses(clusterId, tenantId));
    }
}