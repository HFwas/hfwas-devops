package com.hfwas.devops.user.integration.engine;

import com.hfwas.devops.user.integration.spi.IdentityConnectorHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IdentityConnectorRegistry {

    private final Map<String, IdentityConnectorHandler> handlers;

    public IdentityConnectorRegistry(List<IdentityConnectorHandler> handlerList) {
        Map<String, IdentityConnectorHandler> map = new LinkedHashMap<>();
        for (IdentityConnectorHandler handler : handlerList) {
            map.put(handler.type(), handler);
        }
        this.handlers = Map.copyOf(map);
    }

    public IdentityConnectorHandler require(String type) {
        IdentityConnectorHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的对接类型: " + type);
        }
        return handler;
    }

    public Collection<IdentityConnectorHandler> all() {
        return handlers.values();
    }
}
