package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.field.engine.FieldTypeRegistry;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkItemFieldApplicator {

    private final FieldTypeRegistry fieldTypeRegistry;

    public void apply(PmWorkItem item, String fieldKey, Object rawValue, List<FieldDefinition> definitions) {
        if (item == null || StringUtils.isBlank(fieldKey)) {
            return;
        }
        String key = fieldKey.trim();
        FieldDefinition def = findDefinition(definitions, key);
        if (def != null && def.getSystemFlag() != null && def.getSystemFlag() == 1) {
            applySystemField(item, key, coerceSystemValue(key, rawValue));
            return;
        }
        Map<String, Object> custom = item.getCustomFields() != null
                ? new HashMap<>(item.getCustomFields()) : new HashMap<>();
        if (def != null) {
            var handler = fieldTypeRegistry.get(def.getFieldType());
            handler.validate(def, rawValue);
            Object normalized = handler.normalize(def, rawValue);
            if (normalized == null) {
                custom.remove(key);
            } else {
                custom.put(key, normalized);
            }
        } else if (rawValue == null) {
            custom.remove(key);
        } else {
            custom.put(key, rawValue);
        }
        item.setCustomFields(custom);
    }

    private FieldDefinition findDefinition(List<FieldDefinition> definitions, String fieldKey) {
        if (definitions == null) {
            return null;
        }
        return definitions.stream()
                .filter(def -> fieldKey.equals(def.getFieldKey()))
                .findFirst()
                .orElse(null);
    }

    private void applySystemField(PmWorkItem item, String fieldKey, Object value) {
        switch (fieldKey) {
            case "title" -> item.setTitle(value != null ? String.valueOf(value) : null);
            case "description" -> item.setDescription(value != null ? String.valueOf(value) : null);
            case "priority" -> item.setPriority(value != null ? String.valueOf(value) : null);
            case "assignee_id" -> item.setAssigneeId(toLong(value));
            case "reporter_id" -> item.setReporterId(toLong(value));
            case "module_id" -> item.setModuleId(toLong(value));
            case "parent_id" -> item.setParentId(toLong(value));
            case "sprint_id" -> item.setSprintId(toLong(value));
            default -> {
            }
        }
    }

    private Object coerceSystemValue(String fieldKey, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (fieldKey) {
            case "assignee_id", "reporter_id", "module_id", "parent_id", "sprint_id" -> toLong(rawValue);
            case "title", "description", "priority" -> String.valueOf(rawValue);
            default -> rawValue;
        };
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }
}
