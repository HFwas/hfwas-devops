package com.hfwas.devops.container.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Upload/download files to/from pod containers.
 * <p>
 * Upload uses Fabric8 {@code file().upload()} (same protocol as {@code kubectl cp}).
 * Manual {@code tar xf} + blocking stderr reads hang: the exec process never EOFs
 * stderr until the watch is closed, so the request sits until Kong returns 504.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodFileService {

    private static final long EXEC_TIMEOUT_SECONDS = 50;

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;

    public void uploadFile(Long clusterId, String namespace, String podName,
                           String container, String destPath, MultipartFile file,
                           Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        String filename = sanitizeFileName(file.getOriginalFilename());
        String dir = destPath == null || destPath.isBlank() ? "/tmp/" : destPath;
        dir = dir.endsWith("/") ? dir : dir + "/";
        String remotePath = dir + filename;

        try {
            boolean uploaded = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .inContainer(container)
                    .file(remotePath)
                    .upload(file.getInputStream());
            if (uploaded) {
                log.info("File '{}' uploaded to pod {}/{}/{}:{} ({} bytes)",
                        filename, namespace, podName, container, remotePath, file.getSize());
                return;
            }
            log.info("Fabric8 file upload returned false for {}/{}, falling back to cat", namespace, podName);
        } catch (KubernetesClientException e) {
            throw mapClientException(e, "上传");
        } catch (Exception e) {
            log.info("Fabric8 file upload failed for {}/{}, falling back to cat: {}",
                    namespace, podName, e.getMessage());
        }

        tryUploadWithCat(client, namespace, podName, container, remotePath, file);
        log.info("File '{}' uploaded via cat to pod {}/{}/{}:{} ({} bytes)",
                filename, namespace, podName, container, remotePath, file.getSize());
    }

    /**
     * Fallback for images where Fabric8's upload helper cannot complete.
     * Closes stdin then waits on {@link ExecWatch#exitCode()} — never block on stderr EOF.
     */
    private void tryUploadWithCat(KubernetesClient client, String namespace,
                                  String podName, String container,
                                  String remotePath, MultipartFile file) {
        String escapedPath = remotePath.replace("'", "'\\''");
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try (ExecWatch watch = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .redirectingInput()
                .writingError(err)
                .exec("sh", "-c", "cat > '" + escapedPath + "'")) {

            try (OutputStream stdin = watch.getInput(); InputStream in = file.getInputStream()) {
                in.transferTo(stdin);
            }

            Integer exit = watch.exitCode().get(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String stderr = err.toString().trim();
            if (exit == null || exit != 0 || !stderr.isEmpty()) {
                throw buildUploadException(stderr.isEmpty() ? "exit code " + exit : stderr);
            }
        } catch (TimeoutException e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "上传超时：容器未在限定时间内完成写入");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "文件上传失败: " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "uploaded";
        }
        int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        String name = slash >= 0 ? original.substring(slash + 1) : original;
        return name.isBlank() ? "uploaded" : name;
    }

    private static BizException mapClientException(KubernetesClientException e, String action) {
        int code = e.getCode();
        if (code == 404) {
            return new BizException(ContainerErrorCode.RESOURCE_NOT_FOUND, "Pod 或容器不存在");
        }
        if (code == 403) {
            return new BizException(403, "权限不足");
        }
        return new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                action + "失败: " + e.getMessage());
    }

    private BizException buildUploadException(String stderr) {
        String lower = stderr.toLowerCase();
        if (lower.contains("no such file") || lower.contains("cannot change")
                || lower.contains("cannot access") || lower.contains("not a directory")
                || lower.contains("is a directory")) {
            return new BizException(400, "目标路径不存在");
        }
        if (lower.contains("permission denied") || lower.contains("not permitted")
                || lower.contains("read-only")) {
            return new BizException(403, "权限不足");
        }
        return new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                "上传失败: " + stderr);
    }

    public InputStream downloadFile(Long clusterId, String namespace, String podName,
                                    String container, String filePath,
                                    Long tenantId) {
        ClusterEntity cluster = clusterService.getById(clusterId, tenantId);
        KubernetesClient client = clientFactory.getClient(cluster);

        try {
            return tryDownloadWithFabric8Api(client, namespace, podName, container, filePath);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.info("Fabric8 file copy API failed for {}/{} (alpine?), falling back to cat: {}",
                    namespace, podName, e.getMessage());
        }

        return tryDownloadWithCat(client, namespace, podName, container, filePath);
    }

    private InputStream tryDownloadWithFabric8Api(KubernetesClient client,
                                                  String namespace, String podName,
                                                  String container, String filePath) {
        try {
            InputStream input = client.pods().inNamespace(namespace)
                    .withName(podName)
                    .inContainer(container)
                    .file(filePath)
                    .read();

            byte[] bytes = input.readAllBytes();
            return new ByteArrayInputStream(bytes);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "文件读取失败: " + e.getMessage());
        }
    }

    private InputStream tryDownloadWithCat(KubernetesClient client,
                                           String namespace, String podName,
                                           String container, String filePath) {
        String escapedPath = filePath.replace("'", "'\\''");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();

        try (ExecWatch watch = client.pods().inNamespace(namespace)
                .withName(podName)
                .inContainer(container)
                .redirectingOutput()
                .writingError(errBaos)
                .exec("sh", "-c", "cat '" + escapedPath + "'")) {

            watch.getOutput().transferTo(baos);
            Integer exit = watch.exitCode().get(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String stderr = errBaos.toString().trim();
            if (exit == null) {
                throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED, "下载超时");
            }
            if (!stderr.isEmpty() || exit != 0) {
                throw mapDownloadError(namespace, podName, stderr.isEmpty() ? "exit code " + exit : stderr);
            }
        } catch (TimeoutException e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED, "下载超时");
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "下载文件失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                    "下载文件失败: " + e.getMessage());
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    private BizException mapDownloadError(String namespace, String podName, String stderr) {
        String lower = stderr.toLowerCase();
        log.warn("Download stderr for {}/{}: {}", namespace, podName, stderr);
        if (lower.contains("no such file") || lower.contains("cannot access")) {
            return new BizException(404, "文件不存在");
        }
        if (lower.contains("is a directory")) {
            return new BizException(400, "路径为目录，请指定文件路径");
        }
        if (lower.contains("permission denied")) {
            return new BizException(403, "权限不足");
        }
        return new BizException(ContainerErrorCode.RESOURCE_OPERATION_FAILED,
                "下载失败: " + stderr);
    }
}
