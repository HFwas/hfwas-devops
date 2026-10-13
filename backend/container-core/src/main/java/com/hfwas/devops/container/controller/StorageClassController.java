package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.StorageClassSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class StorageClassController {

    private final ResourceService resourceService;

    @GetMapping("/storageclasses")
    public BaseResult<IPage<StorageClassSummaryVO>> listStorageClasses(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listStorageClasses(clusterId, keyword, pageNo, pageSize, tenantId));
    }
}