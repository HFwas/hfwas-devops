package com.hfwas.devops.pm.workitem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.ResolvedFieldOption;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.module.entity.PmProjectModule;
import com.hfwas.devops.pm.module.mapper.PmProjectModuleMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.user.spi.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkItemFieldDisplayResolver {

    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "low", "低",
            "medium", "中",
            "high", "高",
            "critical", "紧急"
    );

    private final StatusDefinitionService statusDefinitionService;
    private final FieldDefinitionService fieldDefinitionService;
    private final PmProjectModuleMapper moduleMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    public String toLabel(PmWorkItem item, FieldDefinition def, Object value) {
        if (value == null) {
            return null;
        }
        String fieldType = def.getFieldType();
        if ("STATUS".equals(fieldType)) {
            return statusLabel(item.getProjectId(), item.getTypeCode(), String.valueOf(value));
        }
        if ("PRIORITY".equals(fieldType)) {
            return PRIORITY_LABELS.getOrDefault(String.valueOf(value), String.valueOf(value));
        }
        if ("BOOLEAN".equals(fieldType)) {
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) ? "是" : "否";
        }
        if ("USER".equals(fieldType) || "assignee_id".equals(def.getFieldKey()) || "reporter_id".equals(def.getFieldKey())) {
            Long userId = toLong(value);
            return userId == null ? String.valueOf(value) : userDisplayNameResolver.resolve(userId);
        }
        if ("MODULE".equals(fieldType) || "module_id".equals(def.getFieldKey())) {
            Long moduleId = toLong(value);
            if (moduleId == null) {
                return String.valueOf(value);
            }
            PmProjectModule module = moduleMapper.selectById(moduleId);
            return module != null ? module.getName() : String.valueOf(value);
        }
        if ("SELECT".equals(fieldType)) {
            return optionLabel(def, String.valueOf(value));
        }
        if ("MULTI_SELECT".equals(fieldType) && value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(v -> optionLabel(def, String.valueOf(v)))
                    .collect(Collectors.joining(", "));
        }
        if ("MARKDOWN".equals(fieldType) || "TEXTAREA".equals(fieldType)) {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            return text.length() > 80 ? text.substring(0, 80) + "…" : text;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private String statusLabel(Long projectId, String typeCode, String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        return statusDefinitionService.listStatusOptions(projectId, typeCode).stream()
                .filter(s -> code.equals(s.getStatusCode()))
                .map(StatusDefinitionVO::getStatusName)
                .findFirst()
                .orElse(code);
    }

    private String optionLabel(FieldDefinition def, String key) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        Map<String, String> labelMap = optionLabelMap(def);
        return labelMap.getOrDefault(key, key);
    }

    private Map<String, String> optionLabelMap(FieldDefinition def) {
        Map<String, String> map = new HashMap<>();
        if (def.getId() == null) {
            return map;
        }
        List<ResolvedFieldOption> options = fieldDefinitionService.resolveOptions(def.getId());
        for (ResolvedFieldOption option : options) {
            map.put(option.getValue(), option.getLabel());
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
