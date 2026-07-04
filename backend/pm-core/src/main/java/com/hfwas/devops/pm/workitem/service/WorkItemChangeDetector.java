package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.WorkItemFieldChange;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class WorkItemChangeDetector {

    private static final Set<String> IGNORED_KEYS = Set.of("type_code");

    public List<WorkItemFieldChange> detect(PmWorkItem oldItem, PmWorkItem newItem, List<FieldDefinition> definitions) {
        List<WorkItemFieldChange> changes = new ArrayList<>();
        if (newItem == null || definitions == null) {
            return changes;
        }
        for (FieldDefinition def : definitions) {
            if (def.getFieldKey() == null || IGNORED_KEYS.contains(def.getFieldKey())) {
                continue;
            }
            Object oldVal = oldItem == null ? null : readValue(oldItem, def);
            Object newVal = readValue(newItem, def);
            if (valuesEqual(oldVal, newVal)) {
                continue;
            }
            changes.add(new WorkItemFieldChange(
                    def.getFieldKey(),
                    def.getFieldName(),
                    def.getFieldType(),
                    oldVal,
                    newVal
            ));
        }
        return changes;
    }

    private Object readValue(PmWorkItem item, FieldDefinition def) {
        if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
            return readSystemValue(item, def.getFieldKey());
        }
        Map<String, Object> custom = item.getCustomFields();
        return custom != null ? custom.get(def.getFieldKey()) : null;
    }

    private Object readSystemValue(PmWorkItem item, String fieldKey) {
        return switch (fieldKey) {
            case "title" -> item.getTitle();
            case "description" -> item.getDescription();
            case "status" -> item.getStatus();
            case "priority" -> item.getPriority();
            case "assignee_id" -> item.getAssigneeId();
            case "module_id" -> item.getModuleId();
            case "reporter_id" -> item.getReporterId();
            case "parent_id" -> item.getParentId();
            case "sprint_id" -> item.getSprintId();
            default -> null;
        };
    }

    private boolean valuesEqual(Object a, Object b) {
        Object left = normalize(a);
        Object right = normalize(b);
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Collection<?> c1 && right instanceof Collection<?> c2) {
            return new HashSet<>(c1).equals(new HashSet<>(c2));
        }
        return Objects.equals(left, right);
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StringUtils.isBlank(text) ? null : text.trim();
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        return value;
    }
}
