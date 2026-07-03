package com.hfwas.devops.pm.spi.registry;

import com.hfwas.devops.pm.spi.plugin.WorkItemTypePlugin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class WorkItemTypeRegistry {
    private final Map<String, WorkItemTypePlugin> plugins = new HashMap<>();

    public WorkItemTypeRegistry(List<WorkItemTypePlugin> pluginList) {
        for (WorkItemTypePlugin plugin : pluginList) {
            plugins.put(plugin.typeCode(), plugin);
        }
    }

    public Optional<WorkItemTypePlugin> get(String typeCode) {
        return Optional.ofNullable(plugins.get(typeCode));
    }
}
