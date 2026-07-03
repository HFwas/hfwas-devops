package com.hfwas.devops.pm.field.engine;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FieldValidator {

    private final FieldTypeRegistry fieldTypeRegistry;

    public Map<String, Object> validateAndNormalize(PmWorkItem item, List<FieldDefinition> definitions) {
        Map<String, Object> input = item.getCustomFields() == null ? Map.of() : item.getCustomFields();
        Map<String, Object> normalized = new HashMap<>();
        for (FieldDefinition def : definitions) {
            if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
                continue;
            }
            Object raw = input.get(def.getFieldKey());
            var handler = fieldTypeRegistry.get(def.getFieldType());
            handler.validate(def, raw);
            Object value = handler.normalize(def, raw);
            if (value != null) {
                normalized.put(def.getFieldKey(), value);
            }
        }
        return normalized;
    }
}
