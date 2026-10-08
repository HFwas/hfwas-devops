package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.HelmDryRunVO;
import com.hfwas.devops.container.dto.HelmInstallRequest;
import com.hfwas.devops.container.dto.HelmManifestVO;
import com.hfwas.devops.container.dto.HelmReleaseHistoryVO;
import com.hfwas.devops.container.dto.HelmReleaseResourceVO;
import com.hfwas.devops.container.dto.HelmReleaseVO;
import com.hfwas.devops.container.dto.HelmRollbackRequest;
import com.hfwas.devops.container.dto.HelmUpgradeRequest;
import com.hfwas.devops.container.dto.HelmValuesVO;
import com.hfwas.devops.container.service.helm.HelmReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/container/clusters/{clusterId}/helm/releases")
@RequiredArgsConstructor
public class HelmReleaseController {

    private final HelmReleaseService helmReleaseService;

    @GetMapping
    public BaseResult<List<HelmReleaseVO>> list(
            @PathVariable("clusterId") Long clusterId,
            @RequestParam(value = "namespace", required = false) String namespace) {
        return BaseResult.ok(helmReleaseService.list(clusterId, namespace));
    }

    @PostMapping("/{namespace}/dry-run")
    public BaseResult<HelmDryRunVO> dryRunInstall(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @RequestBody HelmInstallRequest request) {
        return BaseResult.ok(helmReleaseService.dryRunInstall(clusterId, namespace, request));
    }

    @PostMapping("/{namespace}")
    public BaseResult<HelmReleaseVO> install(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @RequestBody HelmInstallRequest request) {
        return BaseResult.ok(helmReleaseService.install(clusterId, namespace, request));
    }

    @GetMapping("/{namespace}/{name}/history")
    public BaseResult<List<HelmReleaseHistoryVO>> history(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        return BaseResult.ok(helmReleaseService.history(clusterId, namespace, name));
    }

    @GetMapping("/{namespace}/{name}/values")
    public BaseResult<HelmValuesVO> values(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        return BaseResult.ok(helmReleaseService.values(clusterId, namespace, name));
    }

    @GetMapping("/{namespace}/{name}/manifest")
    public BaseResult<HelmManifestVO> manifest(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        return BaseResult.ok(helmReleaseService.manifest(clusterId, namespace, name));
    }

    @GetMapping("/{namespace}/{name}/resources")
    public BaseResult<List<HelmReleaseResourceVO>> resources(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        return BaseResult.ok(helmReleaseService.resources(clusterId, namespace, name));
    }

    @PutMapping("/{namespace}/{name}/upgrade/dry-run")
    public BaseResult<HelmDryRunVO> dryRunUpgrade(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name,
            @RequestBody HelmUpgradeRequest request) {
        return BaseResult.ok(helmReleaseService.dryRunUpgrade(clusterId, namespace, name, request));
    }

    @PutMapping("/{namespace}/{name}/upgrade")
    public BaseResult<HelmReleaseVO> upgrade(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name,
            @RequestBody HelmUpgradeRequest request) {
        return BaseResult.ok(helmReleaseService.upgrade(clusterId, namespace, name, request));
    }

    @PutMapping("/{namespace}/{name}/rollback")
    public BaseResult<HelmReleaseVO> rollback(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name,
            @RequestBody HelmRollbackRequest request) {
        return BaseResult.ok(helmReleaseService.rollback(clusterId, namespace, name, request));
    }

    @GetMapping("/{namespace}/{name}")
    public BaseResult<HelmReleaseVO> get(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        return BaseResult.ok(helmReleaseService.get(clusterId, namespace, name));
    }

    @DeleteMapping("/{namespace}/{name}")
    public BaseResult<Void> uninstall(
            @PathVariable("clusterId") Long clusterId,
            @PathVariable("namespace") String namespace,
            @PathVariable("name") String name) {
        helmReleaseService.uninstall(clusterId, namespace, name);
        return BaseResult.ok(null);
    }
}
