package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorType;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransitionValidatorExecutor {

    private final StatusDefinitionService statusDefinitionService;

    public void validate(PmWorkItem item, String fromStatus, String transitionId, List<FieldDefinition> definitions) {
        if (item == null || StringUtils.isBlank(transitionId)) {
            return;
        }
        List<TransitionValidatorVO> validators = statusDefinitionService.resolveValidators(
                item.getProjectId(), item.getTypeCode(), fromStatus, transitionId);
        if (validators.isEmpty()) {
            return;
        }
        Map<String, FieldDefinition> defByKey = indexDefinitions(definitions);
        Set<String> requiredKeys = new LinkedHashSet<>();
        for (TransitionValidatorVO validator : validators) {
            if (validator == null || StringUtils.isBlank(validator.getType())) {
                continue;
            }
            String type = validator.getType().trim();
            if (!TransitionValidatorType.REQUIRED_FIELDS.equals(type)) {
                throw new IllegalArgumentException("不支持的流转校验类型: " + type);
            }
            if (validator.getFieldKeys() != null) {
                for (String key : validator.getFieldKeys()) {
                    if (StringUtils.isNotBlank(key)) {
                        requiredKeys.add(key.trim());
                    }
                }
            }
        }
        for (String fieldKey : requiredKeys) {
            if ("status".equals(fieldKey)) {
                continue;
            }
            Object value = readFieldValue(item, fieldKey);
            if (isEmpty(value)) {
                String label = fieldLabel(defByKey.get(fieldKey), fieldKey);
                throw new IllegalArgumentException("流转前必须填写「" + label + "」");
            }
        }
    }

    private Map<String, FieldDefinition> indexDefinitions(List<FieldDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return Map.of();
        }
        return definitions.stream()
                .filter(d -> d.getFieldKey() != null)
                .collect(java.util.stream.Collectors.toMap(
                        FieldDefinition::getFieldKey, d -> d, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private String fieldLabel(FieldDefinition def, String fieldKey) {
        return def != null && StringUtils.isNotBlank(def.getFieldName()) ? def.getFieldName() : fieldKey;
    }

    private Object readFieldValue(PmWorkItem item, String fieldKey) {
        return switch (fieldKey) {
            case "title" -> item.getTitle();
            case "description" -> item.getDescription();
            case "priority" -> item.getPriority();
            case "assignee_id" -> item.getAssigneeId();
            case "reporter_id" -> item.getReporterId();
            case "module_id" -> item.getModuleId();
            case "parent_id" -> item.getParentId();
            case "sprint_id" -> item.getSprintId();
            default -> item.getCustomFields() != null ? item.getCustomFields().get(fieldKey) : null;
        };
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
