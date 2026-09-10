package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.DeployFromImageRequest;
import com.hfwas.devops.container.dto.DeployResultVO;
import com.hfwas.devops.container.service.registry.DeployFromImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/container/registries/{registryId}")
@RequiredArgsConstructor
public class DeployController {

    private final DeployFromImageService deployFromImageService;

    @PostMapping("/deploy")
    public BaseResult<DeployResultVO> deploy(
            @PathVariable Long registryId,
            @Valid @RequestBody DeployFromImageRequest request) {
        return BaseResult.ok(deployFromImageService.deploy(registryId, request));
    }
}