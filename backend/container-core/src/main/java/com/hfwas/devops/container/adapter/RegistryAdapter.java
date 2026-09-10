package com.hfwas.devops.container.adapter;

import com.hfwas.devops.container.dto.*;

import java.util.List;

/**
 * Adapter interface for container image registries.
 * Implementations: HarborAdapter, RegistryV2Adapter (Phase 2).
 */
public interface RegistryAdapter {

    /** Test connectivity to the registry. */
    boolean health();

    /** List all projects / namespaces in the registry. */
    List<RegistryProjectVO> listProjects();

    /** List repositories (images) within a project. */
    List<RegistryRepoVO> listRepositories(String project, int page, int pageSize);

    /** List artifacts (tags/digests) within a repository. */
    List<ArtifactVO> listArtifacts(String project, String repo, int page, int pageSize);

    /** Delete an artifact by digest or tag reference. */
    void deleteArtifact(String project, String repo, String reference);

    /** Get scan overview for an artifact (Harbor Trivy). */
    ScanOverviewVO getScanOverview(String project, String repo, String reference);

    /** List vulnerabilities from a scan report. */
    List<VulnerabilityVO> getVulnerabilities(String project, String repo, String reference, String reportId);
}