package com.hfwas.devops.container.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.service.PodFileService;
import com.hfwas.devops.container.service.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for uploading/downloading files to/from Pod containers.
 * <p>
 * Upload:  POST /container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/upload
 * Download: GET  /container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/download
 */
@RestController
@RequestMapping("/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}")
@RequiredArgsConstructor
public class PodFileController {

    private final PodFileService podFileService;

    /**
     * Upload a file to the pod container.
     *
     * @param clusterId  cluster ID
     * @param namespace  namespace
     * @param name       pod name
     * @param container  target container name
     * @param destPath   destination directory in container (default /tmp)
     * @param file       the file to upload
     */
    @PostMapping("/upload")
    public BaseResult<Void> uploadFile(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam String container,
            @RequestParam(defaultValue = "/tmp") String destPath,
            @RequestParam("file") MultipartFile file) {
        Long tenantId = SecurityHelper.currentTenantId();
        podFileService.uploadFile(clusterId, namespace, name, container, destPath, file, tenantId);
        return BaseResult.ok();
    }

    /**
     * Download a file from the pod container.
     *
     * @param clusterId  cluster ID
     * @param namespace  namespace
     * @param name       pod name
     * @param container  target container name
     * @param path       file path in the container to download
     * @return streaming file response
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam String container,
            @RequestParam String path) {
        Long tenantId = SecurityHelper.currentTenantId();
        InputStream inputStream = podFileService.downloadFile(clusterId, namespace, name, container, path, tenantId);

        // Extract filename from path
        String filename = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            filename = path.substring(lastSlash + 1);
        }

        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(inputStream));
    }
}