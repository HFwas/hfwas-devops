package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.NodeDetailVO;
import com.hfwas.devops.container.dto.NodeSummaryVO;
import com.hfwas.devops.container.service.ResourceService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/container/clusters/{clusterId}")
@RequiredArgsConstructor
public class NodeController {

    private final ResourceService resourceService;

    @GetMapping("/nodes")
    public BaseResult<List<NodeSummaryVO>> listNodes(
            @PathVariable Long clusterId,
            @RequestParam(required = false) String keyword) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.listNodes(clusterId, keyword, tenantId));
    }

    @GetMapping("/nodes/{name}")
    public BaseResult<NodeDetailVO> getNode(
            @PathVariable Long clusterId,
            @PathVariable String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        return BaseResult.ok(resourceService.getNode(clusterId, name, tenantId));
    }
}