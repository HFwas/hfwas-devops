package com.hfwas.devops.container.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for uploading/downloading files to/from pod containers.
 * <p>
 * Upload: packs file into tar stream, pipes via exec "tar xf - -C {dir}".
 * Falls back to "cat > {path}" for containers without tar (e.g. alpine).
 * Download: uses Fabric8 file().copy() API to read tar stream, then untars.
 * Falls back to exec "cat {path}" for containers without tar.
 * <p>
 * Error scenarios (matching design doc):
 * - Container target dir not found  → 400 "目标路径不存在"
 * - File not found                 → 404 "文件不存在"
 * - Path is a directory            → 400 "路径为目录，请指定文件路径"
 * - Permission denied              → 403 "权限不足"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodFileService {

    private static final int BUFFER_SIZE = 8192;

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pod-file-");
        t.setDaemon(true);
        return t;
    });

    // ==================== Upload ====================

    /**
     * Upload a file to the pod container at the specified directory.
     * <p>
     * Primary: packs the file as a tar stream → exec "tar xf - -C {dir}"
     * Fallback: exec "cat > {dir}/{filename}" (alpine compat, no auto mkdir)
     */
    public void uploadFile(Long clusterId, String namespace, String podName,
                           String container, String destPath, MultipartFile file,
                           Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "uploaded";
        }

        String dir = destPath.endsWith("/") ? destPath : destPath + "/";
        byte[] tarData = buildTar(filename, file);

        // 1. Primary: tar-based upload (matches kubectl cp behavior)
        String stderr = tryUploadWithTar(client, namespace, podName, container, dir, tarData);
        if (stderr == null) {
            log.info("File '{}' uploaded via tar to pod {}/{}/{}:{} ({} bytes)",
                    filename, namespace, podName, container, dir, file.getSize());
            return;
        }

        // If tar itself wasn't found, try cat fallback (alpine compat)
        if (stderr.toLowerCase().contains("not found")) {
            log.info("tar not found in container {}/{}, falling back to cat: {}",
                    namespace, podName, stderr);
            tryUploadWithCat(client, namespace, podName, container, dir, filename, file);
            log.info("File '{}' uploaded via cat to pod {}/{}/{}:{} ({} bytes)",
                    filename, namespace, podName, container, dir, file.getSize());
            return;
        }

        // Other tar errors: directory not found, permission denied, etc.
        throw buildUploadException(stderr);
    }

    /** Build a tar archive containing a single file. */
    private byte[] buildTar(String filename, MultipartFile file) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(baos)) {
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                TarArchiveEntry entry = new TarArchiveEntry(filename);
                entry.setSize(file.getSize());
                tarOut.putArchiveEntry(entry);
                try (InputStream in = file.getInputStream()) {
                    in.transferTo(tarOut);
                }
                tarOut.closeArchiveEntry();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "打包 tar 失败: " + e.getMessage());
        }
    }

    /**
     * Try tar-based upload. Returns null on success, stderr string on failure.
     * Never throws.
     */
    private String tryUploadWithTar(KubernetesClient client, String namespace,
                                     String podName, String container,
                                     String destDir, byte[] tarData) {
        try (ExecWatch watch = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .redirectingInput()
                .redirectingError()
                .exec("tar", "xf", "-", "-C", destDir)) {

            OutputStream stdin = watch.getInput();
            stdin.write(tarData);
            stdin.flush();
            stdin.close();

            return readAll(watch.getError(), 1024);
        } catch (Exception e) {
            return "exec error: " + e.getMessage();
        }
    }

    /**
     * Fallback upload via shell cat. No mkdir -p: if directory doesn't exist,
     * the shell will report "No such file or directory", which is detected.
     */
    private void tryUploadWithCat(KubernetesClient client, String namespace,
                                   String podName, String container,
                                   String destDir, String filename, MultipartFile file) {
        String fullPath = destDir + filename;
        String escapedPath = fullPath.replace("'", "'\\''");

        try (ExecWatch watch = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .redirectingInput()
                .redirectingError()
                .exec("sh", "-c", "cat > '" + escapedPath + "'")) {

            try (InputStream fileIn = file.getInputStream()) {
                fileIn.transferTo(watch.getInput());
            }
            watch.getInput().flush();
            watch.getInput().close();

            String stderr = readAll(watch.getError(), 4096);
            if (!stderr.isEmpty()) {
                throw new RuntimeException("cat stderr: " + stderr);
            }

        } catch (IOException e) {
            // The try-with-resources closes ExecWatch; this catches close errors too.
            // Re-throw as runtime so the caller sees it.
            throw new RuntimeException("cat exec error: " + e.getMessage());
        }
    }

    /** Map stderr to the correct BizException per the design doc. */
    private BizException buildUploadException(String stderr) {
        String lower = stderr.toLowerCase();
        if (lower.contains("no such file") || lower.contains("cannot change")
                || lower.contains("cannot access") || lower.contains("not a directory")) {
            return new BizException(400, "目标路径不存在");
        }
        if (lower.contains("permission denied") || lower.contains("not permitted")) {
            return new BizException(403, "权限不足");
        }
        return new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                "上传失败: " + stderr);
    }

    // ==================== Download ====================

    /**
     * Download a file from the pod container.
     * <p>
     * Primary: Fabric8 file().copy() API → untar tar stream
     * Fallback: exec "cat {path}" via shell (alpine compat)
     * <p>
     * Reads the entire file synchronously to catch errors before the HTTP response
     * headers are sent. Returns a ByteArrayInputStream for memory safety
     * (the configured upload limit of 100 MB applies here too).
     */
    public InputStream downloadFile(Long clusterId, String namespace, String podName,
                                     String container, String filePath,
                                     Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        // 1. Primary: Fabric8 file copy API
        try {
            return tryDownloadWithFabric8Api(client, namespace, podName, container, filePath);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.info("Fabric8 file copy API failed for {}/{} (alpine?), falling back to cat: {}",
                    namespace, podName, e.getMessage());
        }

        // 2. Fallback: exec cat — read synchronously to detect errors
        return tryDownloadWithCat(client, namespace, podName, container, filePath);
    }

    /**
     * Download via Fabric8's file copy API.
     * file(path).copy() returns an InputStream (tar archive).
     * We untar and return the raw file content as ByteArrayInputStream.
     */
    private InputStream tryDownloadWithFabric8Api(KubernetesClient client,
                                                   String namespace, String podName,
                                                   String container, String filePath) {
        InputStream tarInput = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .file(filePath)
                .copy();

        // Read and untar synchronously to catch errors
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            if (entry == null || entry.isDirectory()) {
                throw new BizException(400, "路径为目录，请指定文件路径");
            }
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = tarIn.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "文件读取失败: " + e.getMessage());
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Fallback: download via exec "cat" via shell.
     * Reads stdout + stderr synchronously to detect errors.
     */
    private InputStream tryDownloadWithCat(KubernetesClient client,
                                            String namespace, String podName,
                                            String container, String filePath) {
        String escapedPath = filePath.replace("'", "'\\''");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String stderr;

        try (ExecWatch watch = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .redirectingOutput()
                .redirectingError()
                .exec("sh", "-c", "cat '" + escapedPath + "'")) {

            // Read stdout and stderr concurrently to avoid deadlock
            // (stderr may have content while stdout is still being read)
            ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
            Thread errThread = new Thread(() -> {
                try { watch.getError().transferTo(errBaos); } catch (IOException ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            watch.getOutput().transferTo(baos);

            try { errThread.join(5000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            stderr = errBaos.toString().trim();

        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "下载文件失败: " + e.getMessage());
        }

        // Map stderr to appropriate error
        if (!stderr.isEmpty()) {
            String lower = stderr.toLowerCase();
            log.warn("Download stderr for {}/{}: {}", namespace, podName, stderr);
            if (lower.contains("no such file") || lower.contains("cannot access")) {
                throw new BizException(404, "文件不存在");
            }
            if (lower.contains("is a directory")) {
                throw new BizException(400, "路径为目录，请指定文件路径");
            }
            if (lower.contains("permission denied")) {
                throw new BizException(403, "权限不足");
            }
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "下载失败: " + stderr);
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    // ==================== Utilities ====================

    /** Read stderr up to maxBytes. Returns empty string if nothing read. */
    private static String readAll(InputStream stream, int maxBytes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[Math.min(maxBytes, BUFFER_SIZE)];
            int total = 0;
            int n;
            while (total < maxBytes && (n = stream.read(buf, 0, Math.min(buf.length, maxBytes - total))) != -1) {
                baos.write(buf, 0, n);
                total += n;
            }
            return baos.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }
}