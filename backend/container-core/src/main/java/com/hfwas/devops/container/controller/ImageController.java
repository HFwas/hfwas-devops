package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.adapter.RegistryAdapter;
import com.hfwas.devops.container.dto.*;
import com.hfwas.devops.container.entity.RegistryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.registry.RegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for browsing images, tags, and scan results from a registry.
 */
@RestController
@RequestMapping("/container/registries/{registryId}")
@RequiredArgsConstructor
public class ImageController {

    private final RegistryService registryService;

    // ─── Projects ────────────────────────────────────────────

    @GetMapping("/projects")
    public BaseResult<List<RegistryProjectVO>> listProjects(@PathVariable Long registryId) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        return BaseResult.ok(adapter.listProjects());
    }

    // ─── Repositories (images) ────────────────────────────────

    @GetMapping("/projects/{project}/repositories")
    public BaseResult<List<RegistryRepoVO>> listRepositories(
            @PathVariable Long registryId,
            @PathVariable String project,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        return BaseResult.ok(adapter.listRepositories(project, page, pageSize));
    }

    // ─── Artifacts (tags) ─────────────────────────────────────

    @GetMapping("/projects/{project}/repositories/{repo}/artifacts")
    public BaseResult<List<ArtifactVO>> listArtifacts(
            @PathVariable Long registryId,
            @PathVariable String project,
            @PathVariable String repo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        return BaseResult.ok(adapter.listArtifacts(project, repo, page, pageSize));
    }

    // ─── Delete artifact ──────────────────────────────────────

    @DeleteMapping("/projects/{project}/repositories/{repo}/artifacts/{reference}")
    public BaseResult<Void> deleteArtifact(
            @PathVariable Long registryId,
            @PathVariable String project,
            @PathVariable String repo,
            @PathVariable String reference) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        try {
            adapter.deleteArtifact(project, repo, reference);
            return BaseResult.ok();
        } catch (Exception e) {
            throw new com.hfwas.devops.common.error.BizException(
                    ContainerErrorCode.REGISTRY_ARTIFACT_DELETE_FAILED);
        }
    }

    // ─── Scan overview ────────────────────────────────────────

    @GetMapping("/projects/{project}/repositories/{repo}/artifacts/{reference}/scan")
    public BaseResult<ScanOverviewVO> getScanOverview(
            @PathVariable Long registryId,
            @PathVariable String project,
            @PathVariable String repo,
            @PathVariable String reference) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        return BaseResult.ok(adapter.getScanOverview(project, repo, reference));
    }

    // ─── Vulnerabilities ──────────────────────────────────────

    @GetMapping("/projects/{project}/repositories/{repo}/artifacts/{reference}/scan/{reportId}/vulnerabilities")
    public BaseResult<List<VulnerabilityVO>> getVulnerabilities(
            @PathVariable Long registryId,
            @PathVariable String project,
            @PathVariable String repo,
            @PathVariable String reference,
            @PathVariable String reportId) {
        RegistryEntity entity = registryService.findById(registryId);
        RegistryAdapter adapter = registryService.buildAdapter(entity);
        return BaseResult.ok(adapter.getVulnerabilities(project, repo, reference, reportId));
    }
}