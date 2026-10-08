package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One helm invocation's private directory. Kubeconfig and registry config live here and are deleted
 * when the workspace closes, including when the command fails.
 */
final class HelmWorkspace implements AutoCloseable {

    private final Path root;
    private final Path kubeconfigFile;
    private final Map<String, String> environment;

    private HelmWorkspace(Path root, Path kubeconfigFile, Map<String, String> environment) {
        this.root = root;
        this.kubeconfigFile = kubeconfigFile;
        this.environment = environment;
    }

    static HelmWorkspace open(String kubeconfig) {
        Path root;
        try {
            root = HelmTempFiles.createPrivateDir("helm-release-");
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, "无法准备 helm 临时目录");
        }
        try {
            Path kubeconfigFile = root.resolve("kubeconfig");
            Files.writeString(kubeconfigFile, kubeconfig, StandardCharsets.UTF_8);
            HelmTempFiles.ownerOnly(kubeconfigFile, false);
            Path registryDir = root.resolve("config/helm/registry");
            Files.createDirectories(registryDir);
            Map<String, String> env = new LinkedHashMap<>();
            env.put("HOME", root.toString());
            env.put("HELM_CACHE_HOME", root.resolve("cache").toString());
            env.put("HELM_CONFIG_HOME", root.resolve("config").toString());
            env.put("HELM_DATA_HOME", root.resolve("data").toString());
            env.put("HELM_REGISTRY_CONFIG", registryDir.resolve("config.json").toString());
            env.put("KUBECONFIG", kubeconfigFile.toString());
            return new HelmWorkspace(root, kubeconfigFile, Map.copyOf(env));
        } catch (IOException e) {
            HelmTempFiles.deleteRecursively(root);
            throw new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, "无法准备 helm 临时目录");
        }
    }

    Path root() {
        return root;
    }

    Path kubeconfigFile() {
        return kubeconfigFile;
    }

    Map<String, String> environment() {
        return environment;
    }

    Path writeValues(String yaml) {
        try {
            Path file = root.resolve("values.yaml");
            Files.writeString(file, yaml == null ? "" : yaml, StandardCharsets.UTF_8);
            HelmTempFiles.ownerOnly(file, false);
            return file;
        } catch (IOException e) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, "无法写入 Values 文件");
        }
    }

    @Override
    public void close() {
        HelmTempFiles.deleteRecursively(root);
    }
}
