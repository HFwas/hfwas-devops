package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.EventVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/container/clusters/{clusterId}/namespaces/{namespace}/events")
@RequiredArgsConstructor
public class EventController {

    private final ResourceService resourceService;

    @GetMapping
    public BaseResult<List<EventVO>> listEvents(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @RequestParam(required = false) String uid) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listEvents(clusterId, namespace, uid, tenantId));
    }
}