package com.hfwas.devops.container.service.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.DeployFromImageRequest;
import com.hfwas.devops.container.dto.DeployResultVO;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.entity.RegistryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.SecurityHelper;
import com.hfwas.devops.container.service.cluster.ClusterKubernetesClientFactory;
import com.hfwas.devops.container.service.cluster.ClusterService;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployFromImageService {

    private final ClusterService clusterService;
    private final ClusterKubernetesClientFactory clientFactory;
    private final RegistryService registryService;
    private final RegistryCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;

    /**
     * Deploy an image from a registry to a target cluster namespace.
     * Automatically creates ImagePullSecret if requested.
     */
    @Transactional
    public DeployResultVO deploy(Long registryId, DeployFromImageRequest request) {
        Long tenantId = SecurityHelper.currentTenantId();

        // 1. Validate cluster visibility
        ClusterEntity cluster = clusterService.getById(request.getClusterId(), tenantId);
        RegistryEntity registry = registryService.findById(registryId);

        // 2. Build full image URL
        String registryHost = resolveRegistryHost(registry);
        String fullImage = registryHost + "/" + request.getImage();

        // 3. Get K8s client
        KubernetesClient client = clientFactory.getClient(cluster);

        // 4. Verify namespace exists
        if (client.namespaces().withName(request.getNamespace()).get() == null) {
            throw new BizException(ContainerErrorCode.REGISTRY_NAMESPACE_REQUIRED);
        }

        // 5. Create ImagePullSecret (default: true)
        String pullSecretName = null;
        boolean createPullSecret = request.getCreatePullSecret() == null || request.getCreatePullSecret();
        if (createPullSecret) {
            pullSecretName = request.getPullSecretName() != null
                    ? request.getPullSecretName()
                    : registry.getName() + "-pull-secret";
            createPullSecretIfNotExists(client, request.getNamespace(), pullSecretName, registry, registryHost);
            ensureServiceAccountHasPullSecret(client, request.getNamespace(), pullSecretName);
        }

        // 6. Create Deployment
        Deployment deployment = buildDeployment(fullImage, request);
        client.apps().deployments().inNamespace(request.getNamespace()).resource(deployment).create();

        // 7. Create Service (optional)
        String serviceName = null;
        if (request.getContainerPort() != null && request.getContainerPort() > 0) {
            serviceName = request.getName();
            io.fabric8.kubernetes.api.model.Service svc = buildService(request);
            client.services().inNamespace(request.getNamespace()).resource(svc).create();
        }

        log.info("Deployed image {} to cluster={} namespace={} deployment={}",
                fullImage, request.getClusterId(), request.getNamespace(), request.getName());
        return new DeployResultVO(request.getClusterId(), request.getNamespace(),
                request.getName(), pullSecretName, serviceName);
    }

    // ---- Image URL construction ----

    /**
     * Build the full image URL for K8s deployment.
     * <p>
     * For built-in Harbor registries on the same K3s cluster, uses the K8s
     * internal service DNS ({@code harbor.harbor.svc.cluster.local}) so that
     * pods can reach the registry regardless of how the registry URL was
     * configured (NodePort, ClusterIP, etc.).
     * </p>
     */
    private String buildImageUrl(RegistryEntity registry, String image) {
        String host = resolveRegistryHost(registry);
        return host + "/" + image;
    }

    /**
     * Resolve the registry hostname:port for K8s pod access.
     * Strips protocol prefix but preserves the port.
     * For built-in registries on the same cluster, uses the K8s service DNS.
     */
    private String resolveRegistryHost(RegistryEntity registry) {
        // Built-in Harbor on the same K8s cluster — use internal service DNS
        if ("builtin".equals(registry.getSource()) && registry.getClusterId() != null) {
            return "harbor.harbor.svc.cluster.local";
        }
        String url = registry.getUrl();
        if (url.startsWith("https://")) url = url.substring(8);
        if (url.startsWith("http://")) url = url.substring(7);
        return url;
    }

    // ---- ImagePullSecret creation ----

    private void createPullSecretIfNotExists(KubernetesClient client, String namespace,
                                              String secretName, RegistryEntity registry, String registryHost) {
        Secret existing = client.secrets().inNamespace(namespace).withName(secretName).get();
        if (existing != null) {
            log.info("ImagePullSecret {} already exists in namespace {}, skipping", secretName, namespace);
            return;
        }

        String password = credentialCipher.decrypt(registry.getCredentialPassword());
        String authHeader = Base64.getEncoder().encodeToString(
                (registry.getCredentialUsername() + ":" + (password != null ? password : "")).getBytes());

        Map<String, Object> dockerConfig = new HashMap<>();
        Map<String, Object> auths = new HashMap<>();
        Map<String, Object> creds = new HashMap<>();
        creds.put("username", registry.getCredentialUsername());
        creds.put("password", password != null ? password : "");
        creds.put("auth", authHeader);
        auths.put(registryHost, creds);
        dockerConfig.put("auths", auths);

        try {
            String dockerConfigJson = objectMapper.writeValueAsString(dockerConfig);
            String encodedConfig = Base64.getEncoder().encodeToString(dockerConfigJson.getBytes());

            Secret secret = new SecretBuilder()
                    .withNewMetadata()
                        .withName(secretName)
                        .withLabels(Map.of("app.kubernetes.io/managed-by", "hfwas-container"))
                    .endMetadata()
                    .withType("kubernetes.io/dockerconfigjson")
                    .withData(Map.of(".dockerconfigjson", encodedConfig))
                    .build();

            client.secrets().inNamespace(namespace).resource(secret).create();
            log.info("Created ImagePullSecret {}/{} for registry {}", namespace, secretName, registry.getName());
        } catch (Exception e) {
            throw new BizException(ContainerErrorCode.REGISTRY_DEPLOY_FAILED, "创建 ImagePullSecret 失败: " + e.getMessage());
        }
    }

    private void ensureServiceAccountHasPullSecret(KubernetesClient client, String namespace, String secretName) {
        ServiceAccount sa = client.serviceAccounts().inNamespace(namespace).withName("default").get();
        if (sa == null) return;

        boolean alreadyExists = sa.getImagePullSecrets() != null &&
                sa.getImagePullSecrets().stream().anyMatch(ips -> secretName.equals(ips.getName()));
        if (alreadyExists) return;

        List<LocalObjectReference> pullSecrets = sa.getImagePullSecrets();
        if (pullSecrets == null) {
            pullSecrets = new ArrayList<>();
        }
        pullSecrets.add(new LocalObjectReference(secretName));
        sa.setImagePullSecrets(pullSecrets);

        client.serviceAccounts().inNamespace(namespace).resource(sa).update();
        log.info("Added ImagePullSecret {} to default ServiceAccount in namespace {}", secretName, namespace);
    }

    // ---- Deployment builder ----

    private Deployment buildDeployment(String fullImage, DeployFromImageRequest request) {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", request.getName());
        labels.put("app.kubernetes.io/managed-by", "hfwas-container");

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName(request.getName())
                .withImage(fullImage)
                .withImagePullPolicy("IfNotPresent");

        // Container port
        if (request.getContainerPort() != null && request.getContainerPort() > 0) {
            containerBuilder.withPorts(new ContainerPortBuilder()
                    .withContainerPort(request.getContainerPort())
                    .build());
        }

        // Environment variables
        if (request.getEnv() != null && !request.getEnv().isEmpty()) {
            List<EnvVar> envVars = request.getEnv().stream()
                    .map(e -> new EnvVar(e.getName(), e.getValue(), null))
                    .toList();
            containerBuilder.withEnv(envVars);
        }

        // Resource limits
        if (request.getResources() != null) {
            Map<String, io.fabric8.kubernetes.api.model.Quantity> limits = new HashMap<>();
            Map<String, io.fabric8.kubernetes.api.model.Quantity> requests = new HashMap<>();
            if (request.getResources().getCpu() != null) {
                limits.put("cpu", new io.fabric8.kubernetes.api.model.Quantity(request.getResources().getCpu()));
                requests.put("cpu", new io.fabric8.kubernetes.api.model.Quantity(request.getResources().getCpu()));
            }
            if (request.getResources().getMemory() != null) {
                limits.put("memory", new io.fabric8.kubernetes.api.model.Quantity(request.getResources().getMemory()));
                requests.put("memory", new io.fabric8.kubernetes.api.model.Quantity(request.getResources().getMemory()));
            }
            if (!limits.isEmpty()) {
                containerBuilder.withNewResources()
                        .withLimits(limits)
                        .withRequests(requests)
                        .endResources();
            }
        }

        Container container = containerBuilder.build();

        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(request.getName())
                    .withNamespace(request.getNamespace())
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(request.getReplicas() != null ? request.getReplicas() : 1)
                    .withNewSelector()
                        .withMatchLabels(Map.of("app", request.getName()))
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(labels)
                        .endMetadata()
                        .withNewSpec()
                            .withContainers(container)
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    // ---- Service builder ----

    private io.fabric8.kubernetes.api.model.Service buildService(DeployFromImageRequest request) {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", request.getName());
        labels.put("app.kubernetes.io/managed-by", "hfwas-container");

        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(request.getName())
                    .withNamespace(request.getNamespace())
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withSelector(Map.of("app", request.getName()))
                    .withPorts(new ServicePortBuilder()
                            .withPort(request.getContainerPort())
                            .withTargetPort(new IntOrString(request.getContainerPort()))
                            .withProtocol("TCP")
                            .build())
                    .withType("ClusterIP")
                .endSpec()
                .build();
    }
}