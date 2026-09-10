package com.hfwas.devops.container.service.registry;

import com.hfwas.devops.container.adapter.RegistryAdapter;
import com.hfwas.devops.container.entity.RegistryEntity;
import com.hfwas.devops.container.mapper.RegistryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic heartbeat that checks registry connectivity and updates status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryHeartbeatJob {

    private final RegistryMapper registryMapper;
    private final RegistryService registryService;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void heartbeat() {
        List<RegistryEntity> registries = registryService.listAll();
        for (RegistryEntity registry : registries) {
            try {
                RegistryAdapter adapter = registryService.buildAdapter(registry);
                boolean healthy = adapter.health();

                String newStatus = healthy ? "Connected" : "Error";
                if (!newStatus.equals(registry.getStatus())) {
                    registry.setStatus(newStatus);
                    registry.setLastError(healthy ? "" : "心跳检测失败");
                    registry.setUpdatedAt(LocalDateTime.now());
                    registryMapper.updateById(registry);
                    log.debug("Registry heartbeat: id={} name={} status={}", registry.getId(), registry.getName(), newStatus);
                }
            } catch (Exception e) {
                log.warn("Registry heartbeat failed for id={}: {}", registry.getId(), e.getMessage());
                if (!"Error".equals(registry.getStatus())) {
                    registry.setStatus("Error");
                    registry.setLastError(e.getMessage());
                    registry.setUpdatedAt(LocalDateTime.now());
                    registryMapper.updateById(registry);
                }
            }
        }
    }
}