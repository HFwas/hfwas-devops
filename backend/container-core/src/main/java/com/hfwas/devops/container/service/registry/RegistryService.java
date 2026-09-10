package com.hfwas.devops.container.service.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.adapter.HarborAdapter;
import com.hfwas.devops.container.adapter.RegistryAdapter;
import com.hfwas.devops.container.dto.RegistrySaveDTO;
import com.hfwas.devops.container.dto.RegistryUpdateDTO;
import com.hfwas.devops.container.dto.RegistryVO;
import com.hfwas.devops.container.entity.RegistryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.mapper.RegistryMapper;
import com.hfwas.devops.container.service.SecurityHelper;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryService {

    private final RegistryMapper registryMapper;
    private final RegistryCredentialCipher credentialCipher;

    // ---- CRUD ----

    @Transactional
    public Long create(RegistrySaveDTO dto) {
        Long tenantId = SecurityHelper.currentTenantId();

        RegistryEntity entity = new RegistryEntity();
        entity.setTenantId(tenantId);
        entity.setName(dto.getName());
        entity.setAlias(dto.getAlias());
        entity.setType(dto.getType() != null ? dto.getType() : "harbor");
        entity.setUrl(dto.getUrl());
        entity.setInsecure(dto.getInsecure() != null ? dto.getInsecure() : false);
        entity.setCredentialUsername(dto.getCredentialUsername());
        entity.setCredentialPassword(credentialCipher.encrypt(dto.getCredentialPassword()));
        entity.setSource("manual");
        entity.setClusterId(dto.getClusterId());
        entity.setStatus("Unknown");
        entity.setLabels("{}");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        registryMapper.insert(entity);

        // Test connection immediately
        try {
            RegistryAdapter adapter = buildAdapter(entity);
            boolean ok = adapter.health();
            entity.setStatus(ok ? "Connected" : "Error");
            if (!ok) entity.setLastError("连接测试失败");
        } catch (Exception e) {
            entity.setStatus("Error");
            entity.setLastError(e.getMessage());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(entity);

        return entity.getId();
    }

    public IPage<RegistryVO> page(int pageNo, int pageSize) {
        Long tenantId = SecurityHelper.currentTenantId();
        LambdaQueryWrapper<RegistryEntity> wrapper = new LambdaQueryWrapper<RegistryEntity>()
                .eq(tenantId != null, RegistryEntity::getTenantId, tenantId)
                .orderByDesc(RegistryEntity::getUpdatedAt);
        IPage<RegistryEntity> page = registryMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public RegistryVO getById(Long id) {
        RegistryEntity entity = findById(id);
        return toVO(entity);
    }

    public RegistryEntity findById(Long id) {
        Long tenantId = SecurityHelper.currentTenantId();
        RegistryEntity entity = registryMapper.selectById(id);
        if (entity == null || (tenantId != null && !tenantId.equals(entity.getTenantId()))) {
            throw new BizException(ContainerErrorCode.REGISTRY_FORBIDDEN);
        }
        return entity;
    }

    @Transactional
    public void update(Long id, RegistryUpdateDTO dto) {
        RegistryEntity entity = findById(id);
        if (dto.getAlias() != null) entity.setAlias(dto.getAlias());
        if (dto.getUrl() != null) entity.setUrl(dto.getUrl());
        if (dto.getInsecure() != null) entity.setInsecure(dto.getInsecure());
        if (dto.getCredentialUsername() != null) entity.setCredentialUsername(dto.getCredentialUsername());
        if (dto.getCredentialPassword() != null && !dto.getCredentialPassword().isBlank()) {
            entity.setCredentialPassword(credentialCipher.encrypt(dto.getCredentialPassword()));
        }
        if (dto.getLabels() != null) entity.setLabels(dto.getLabels().toString());
        entity.setUpdatedAt(LocalDateTime.now());
        registryMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        RegistryEntity entity = findById(id);
        registryMapper.deleteById(id);
        log.info("Registry deleted: id={} name={}", id, entity.getName());
    }

    // ---- connection test ----

    public boolean testConnection(Long id) {
        RegistryEntity entity = findById(id);
        try {
            RegistryAdapter adapter = buildAdapter(entity);
            boolean ok = adapter.health();
            entity.setStatus(ok ? "Connected" : "Error");
            entity.setLastError(ok ? "" : "连接测试失败");
            entity.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(entity);
            return ok;
        } catch (Exception e) {
            entity.setStatus("Error");
            entity.setLastError(e.getMessage());
            entity.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(entity);
            log.warn("Connection test failed for registry {}: {}", id, e.getMessage());
            return false;
        }
    }

    // ---- adapter routing ----

    public RegistryAdapter buildAdapter(RegistryEntity entity) {
        String password = credentialCipher.decrypt(entity.getCredentialPassword());
        return switch (entity.getType()) {
            case "harbor" -> new HarborAdapter(
                    entity.getUrl(),
                    entity.getCredentialUsername(),
                    password,
                    entity.getInsecure()
            );
            default -> throw new IllegalArgumentException("Unsupported registry type: " + entity.getType());
        };
    }

    // ---- builtin discovery ----

    /**
     * Auto-discover built-in Harbor in a given K8s cluster.
     * Checks the 'harbor' namespace for Harbor services and admin password secret.
     */
    public Optional<RegistryEntity> discoverBuiltin(KubernetesClient k8sClient, Long clusterId) {
        try {
            // Check if harbor namespace exists
            if (k8sClient.namespaces().withName("harbor").get() == null) {
                return Optional.empty();
            }

            // Find harbor service
            io.fabric8.kubernetes.api.model.Service harborSvc = k8sClient.services().inNamespace("harbor").withName("harbor").get();
            if (harborSvc == null) {
                return Optional.empty();
            }

            // Get cluster IP and port
            String clusterIP = harborSvc.getSpec().getClusterIP();
            Integer port = harborSvc.getSpec().getPorts().stream()
                    .filter(p -> "http".equals(p.getName()) || p.getPort() == 80)
                    .findFirst()
                    .map(p -> p.getPort())
                    .orElse(80);

            // Get admin password from secret
            Secret secret = k8sClient.secrets().inNamespace("harbor").withName("harbor-core").get();
            String password = "Harbor12345"; // default fallback
            if (secret != null && secret.getData() != null) {
                String encoded = secret.getData().get("HARBOR_ADMIN_PASSWORD");
                if (encoded != null) {
                    password = new String(Base64.getDecoder().decode(encoded));
                }
            }

            String url = "http://" + clusterIP + ":" + port;
            log.info("Discovered built-in Harbor at {} for cluster id={}", url, clusterId);

            RegistryEntity entity = new RegistryEntity();
            entity.setTenantId(SecurityHelper.currentTenantId());
            entity.setName(clusterId + "-builtin-harbor");
            entity.setAlias("内置 Harbor");
            entity.setType("harbor");
            entity.setUrl(url);
            entity.setInsecure(true);
            entity.setCredentialUsername("admin");
            entity.setCredentialPassword(credentialCipher.encrypt(password));
            entity.setSource("builtin");
            entity.setClusterId(clusterId);
            entity.setStatus("Connected");
            entity.setLabels("{}");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            return Optional.of(entity);
        } catch (Exception e) {
            log.warn("Failed to discover built-in Harbor for cluster {}: {}", clusterId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Save or update a builtin registry for a cluster.
     * If already exists (same name+tenant), re-use and update credentials.
     */
    @Transactional
    public void saveBuiltinRegistry(RegistryEntity builtin) {
        Long tenantId = builtin.getTenantId();
        LambdaQueryWrapper<RegistryEntity> wrapper = new LambdaQueryWrapper<RegistryEntity>()
                .eq(RegistryEntity::getTenantId, tenantId)
                .eq(RegistryEntity::getName, builtin.getName());

        RegistryEntity existing = registryMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setUrl(builtin.getUrl());
            existing.setCredentialUsername(builtin.getCredentialUsername());
            existing.setCredentialPassword(builtin.getCredentialPassword());
            existing.setStatus("Connected");
            existing.setUpdatedAt(LocalDateTime.now());
            registryMapper.updateById(existing);
            log.info("Updated builtin registry: id={}", existing.getId());
        } else {
            registryMapper.insert(builtin);
            log.info("Created builtin registry: name={}", builtin.getName());
        }
    }

    /**
     * Get a registry entity by its name (within tenant scope).
     */
    public RegistryEntity getByName(String name) {
        Long tenantId = SecurityHelper.currentTenantId();
        LambdaQueryWrapper<RegistryEntity> wrapper = new LambdaQueryWrapper<RegistryEntity>()
                .eq(RegistryEntity::getTenantId, tenantId)
                .eq(RegistryEntity::getName, name);
        RegistryEntity entity = registryMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BizException(ContainerErrorCode.REGISTRY_NOT_FOUND);
        }
        return entity;
    }

    /**
     * List all registries for heartbeat job (across tenants).
     */
    public List<RegistryEntity> listAll() {
        return registryMapper.selectList(null);
    }

    /**
     * List registries scoped to the current tenant.
     */
    public List<RegistryEntity> listByTenant() {
        Long tenantId = SecurityHelper.currentTenantId();
        LambdaQueryWrapper<RegistryEntity> wrapper = new LambdaQueryWrapper<RegistryEntity>()
                .eq(tenantId != null, RegistryEntity::getTenantId, tenantId);
        return registryMapper.selectList(wrapper);
    }

    // ---- internal ----

    private RegistryVO toVO(RegistryEntity entity) {
        RegistryVO vo = new RegistryVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setName(entity.getName());
        vo.setAlias(entity.getAlias());
        vo.setType(entity.getType());
        vo.setUrl(entity.getUrl());
        vo.setInsecure(entity.getInsecure());
        vo.setCredentialUsername(entity.getCredentialUsername());
        vo.setSource(entity.getSource());
        vo.setClusterId(entity.getClusterId());
        vo.setStatus(entity.getStatus());
        vo.setLastError(entity.getLastError());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        // credentialPassword is NEVER included in VO
        return vo;
    }
}