package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.HelmChartArtifactVO;
import com.hfwas.devops.container.dto.HelmChartDetailVO;
import com.hfwas.devops.container.dto.HelmChartRepositoryVO;
import com.hfwas.devops.container.dto.HelmChartSummaryVO;
import com.hfwas.devops.container.dto.HelmValuesVO;
import com.hfwas.devops.container.error.HelmChartConflictException;
import com.hfwas.devops.container.service.helm.HelmChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Upload a Helm chart into Harbor OCI and read the catalog.
 * Release install and upgrade live on {@link HelmReleaseController}.
 */
@RestController
@RequestMapping("/container/helm")
@RequiredArgsConstructor
public class HelmChartController {

    private final HelmChartService helmChartService;

    @PostMapping(value = "/charts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResult<HelmChartArtifactVO>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "repositoryId", required = false) Long repositoryId) {
        try {
            return ResponseEntity.ok(BaseResult.ok(helmChartService.upload(file, repositoryId)));
        } catch (HelmChartConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BaseResult.failed(ex.getCode(), ex.getMessage()));
        }
    }

    @GetMapping("/charts")
    public BaseResult<List<HelmChartSummaryVO>> listCharts(
            @RequestParam(value = "repositoryId", required = false) Long repositoryId,
            @RequestParam(value = "name", required = false) String name) {
        return BaseResult.ok(helmChartService.listCharts(repositoryId, name));
    }

    @GetMapping("/charts/{name}")
    public BaseResult<HelmChartDetailVO> getChart(
            @PathVariable("name") String name,
            @RequestParam(value = "repositoryId", required = false) Long repositoryId,
            @RequestParam(value = "version", required = false) String version) {
        return BaseResult.ok(helmChartService.getChart(name, repositoryId, version));
    }

    @GetMapping("/charts/{name}/versions/{version:.+}/values")
    public BaseResult<HelmValuesVO> getValues(
            @PathVariable("name") String name,
            @PathVariable("version") String version,
            @RequestParam(value = "repositoryId", required = false) Long repositoryId) {
        return BaseResult.ok(helmChartService.getValues(name, version, repositoryId));
    }

    @GetMapping("/charts/{name}/versions")
    public BaseResult<List<HelmChartArtifactVO>> listVersions(
            @PathVariable("name") String name,
            @RequestParam(value = "repositoryId", required = false) Long repositoryId) {
        return BaseResult.ok(helmChartService.listVersions(name, repositoryId));
    }

    @GetMapping("/charts/{name}/versions/{version:.+}")
    public BaseResult<HelmChartArtifactVO> getVersion(
            @PathVariable("name") String name,
            @PathVariable("version") String version,
            @RequestParam(value = "repositoryId", required = false) Long repositoryId) {
        return BaseResult.ok(helmChartService.getVersion(name, version, repositoryId));
    }

    @GetMapping("/repositories")
    public BaseResult<List<HelmChartRepositoryVO>> listRepositories() {
        return BaseResult.ok(helmChartService.listRepositories());
    }
}
