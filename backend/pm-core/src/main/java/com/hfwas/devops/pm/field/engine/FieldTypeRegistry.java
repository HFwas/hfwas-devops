package com.hfwas.devops.pm.field.engine;

import com.hfwas.devops.pm.field.model.FieldType;
import com.hfwas.devops.pm.field.spi.FieldTypeHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class FieldTypeRegistry {
    private final Map<FieldType, FieldTypeHandler> handlers = new EnumMap<>(FieldType.class);

    public FieldTypeRegistry(List<FieldTypeHandler> handlerList) {
        for (FieldTypeHandler handler : handlerList) {
            handlers.put(handler.type(), handler);
        }
    }

    public FieldTypeHandler get(FieldType type) {
        FieldTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported field type: " + type);
        }
        return handler;
    }

    public FieldTypeHandler get(String typeCode) {
        return get(FieldType.valueOf(typeCode));
    }
}
