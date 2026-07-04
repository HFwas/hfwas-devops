package com.hfwas.devops.pm.workitem.io;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.ResolvedFieldOption;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.module.entity.PmProjectModule;
import com.hfwas.devops.pm.module.mapper.PmProjectModuleMapper;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.StatusDefinitionVO;
import com.hfwas.devops.pm.workitem.service.StatusDefinitionService;
import com.hfwas.devops.user.spi.UserIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkItemIoValueCodec {

    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "low", "低", "medium", "中", "high", "高", "critical", "紧急");
    private static final Map<String, String> PRIORITY_BY_LABEL = PRIORITY_LABELS.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

    private final FieldDefinitionService fieldDefinitionService;
    private final StatusDefinitionService statusDefinitionService;
    private final PmProjectModuleMapper moduleMapper;
    private final UserIdentityResolver userIdentityResolver;

    public Object exportValue(PmWorkItem item, WorkItemIoColumn column, FieldDefinition def) {
        if (WorkItemIoFieldRegistry.ITEM_KEY.equals(column.getFieldKey())) {
            return item.getItemKey();
        }
        Object raw = readRaw(item, column, def);
        if (raw == null) {
            return "";
        }
        String fieldType = column.getFieldType();
        if ("USER".equals(fieldType) || "assignee_id".equals(column.getFieldKey()) || "reporter_id".equals(column.getFieldKey())) {
            Long userId = toLong(raw);
            return userId == null ? raw : StringUtils.defaultString(userIdentityResolver.resolveUsername(userId));
        }
        if ("MODULE".equals(fieldType) || "module_id".equals(column.getFieldKey())) {
            Long moduleId = toLong(raw);
            if (moduleId == null) {
                return raw;
            }
            PmProjectModule module = moduleMapper.selectById(moduleId);
            return module != null ? module.getName() : raw;
        }
        if ("STATUS".equals(fieldType)) {
            return statusLabel(item.getProjectId(), item.getTypeCode(), String.valueOf(raw));
        }
        if ("PRIORITY".equals(fieldType)) {
            return PRIORITY_LABELS.getOrDefault(String.valueOf(raw), String.valueOf(raw));
        }
        if ("BOOLEAN".equals(fieldType)) {
            return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw)) ? "是" : "否";
        }
        if ("MULTI_SELECT".equals(fieldType) && raw instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return raw;
    }

    public Object importValue(String text, WorkItemIoColumn column, FieldDefinition def,
                              Long projectId, String typeCode) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (WorkItemIoFieldRegistry.ITEM_KEY.equals(column.getFieldKey())) {
            return trimmed;
        }
        String fieldType = column.getFieldType();
        if ("USER".equals(fieldType) || "assignee_id".equals(column.getFieldKey()) || "reporter_id".equals(column.getFieldKey())) {
            Long id = userIdentityResolver.resolveByUsername(trimmed);
            if (id == null) {
                id = userIdentityResolver.resolveByDisplayName(trimmed);
            }
            if (id == null) {
                throw new IllegalArgumentException("未找到用户: " + trimmed);
            }
            return id;
        }
        if ("MODULE".equals(fieldType) || "module_id".equals(column.getFieldKey())) {
            Long moduleId = resolveModuleId(projectId, trimmed);
            if (moduleId == null) {
                throw new IllegalArgumentException("未找到模块: " + trimmed);
            }
            return moduleId;
        }
        if ("STATUS".equals(fieldType)) {
            return resolveStatusCode(projectId, typeCode, trimmed);
        }
        if ("PRIORITY".equals(fieldType)) {
            return PRIORITY_BY_LABEL.getOrDefault(trimmed, trimmed);
        }
        if ("BOOLEAN".equals(fieldType)) {
            return "是".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "1".equals(trimmed) || "Y".equalsIgnoreCase(trimmed);
        }
        if ("NUMBER".equals(fieldType)) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("数字格式错误: " + trimmed);
            }
        }
        if ("DATE".equals(fieldType)) {
            return parseDate(trimmed);
        }
        if ("SELECT".equals(fieldType) && def != null) {
            return resolveOptionKey(def, trimmed);
        }
        if ("MULTI_SELECT".equals(fieldType) && def != null) {
            List<String> keys = new ArrayList<>();
            for (String part : trimmed.split("[,，]")) {
                if (StringUtils.isNotBlank(part)) {
                    keys.add(resolveOptionKey(def, part.trim()));
                }
            }
            return keys;
        }
        return trimmed;
    }

    public void applyValue(PmWorkItem item, WorkItemIoColumn column, FieldDefinition def, Object value) {
        if (value == null || WorkItemIoFieldRegistry.ITEM_KEY.equals(column.getFieldKey())) {
            return;
        }
        if (column.isSystemField()) {
            applySystem(item, column.getFieldKey(), value);
            return;
        }
        Map<String, Object> custom = item.getCustomFields();
        if (custom == null) {
            custom = new LinkedHashMap<>();
            item.setCustomFields(custom);
        }
        custom.put(column.getFieldKey(), value);
    }

    private void applySystem(PmWorkItem item, String fieldKey, Object value) {
        switch (fieldKey) {
            case "title" -> item.setTitle(String.valueOf(value));
            case "description" -> item.setDescription(String.valueOf(value));
            case "status" -> item.setStatus(String.valueOf(value));
            case "priority" -> item.setPriority(String.valueOf(value));
            case "assignee_id" -> item.setAssigneeId(toLong(value));
            case "reporter_id" -> item.setReporterId(toLong(value));
            case "module_id" -> item.setModuleId(toLong(value));
            default -> {
            }
        }
    }

    private Object readRaw(PmWorkItem item, WorkItemIoColumn column, FieldDefinition def) {
        if (column.isSystemField() && !WorkItemIoFieldRegistry.ITEM_KEY.equals(column.getFieldKey())) {
            return switch (column.getFieldKey()) {
                case "title" -> item.getTitle();
                case "description" -> item.getDescription();
                case "status" -> item.getStatus();
                case "priority" -> item.getPriority();
                case "assignee_id" -> item.getAssigneeId();
                case "reporter_id" -> item.getReporterId();
                case "module_id" -> item.getModuleId();
                default -> null;
            };
        }
        return item.getCustomFields() != null ? item.getCustomFields().get(column.getFieldKey()) : null;
    }

    private String statusLabel(Long projectId, String typeCode, String code) {
        return statusDefinitionService.listStatusOptions(projectId, typeCode).stream()
                .filter(s -> code.equals(s.getStatusCode()))
                .map(StatusDefinitionVO::getStatusName)
                .findFirst()
                .orElse(code);
    }

    private String resolveStatusCode(Long projectId, String typeCode, String text) {
        for (StatusDefinitionVO status : statusDefinitionService.listStatusOptions(projectId, typeCode)) {
            if (text.equals(status.getStatusCode()) || text.equals(status.getStatusName())) {
                return status.getStatusCode();
            }
        }
        throw new IllegalArgumentException("未找到状态: " + text);
    }

    private String resolveOptionKey(FieldDefinition def, String text) {
        if (def.getId() == null) {
            return text;
        }
        for (ResolvedFieldOption option : fieldDefinitionService.resolveOptions(def.getId())) {
            if (text.equals(option.getValue()) || text.equals(option.getLabel())) {
                return option.getValue();
            }
        }
        return text;
    }

    private Long resolveModuleId(Long projectId, String name) {
        List<PmProjectModule> modules = moduleMapper.selectList(Wrappers.<PmProjectModule>lambdaQuery()
                .eq(PmProjectModule::getProjectId, projectId)
                .eq(PmProjectModule::getDelFlag, 0));
        for (PmProjectModule module : modules) {
            if (name.equals(module.getName())) {
                return module.getId();
            }
        }
        return null;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式错误: " + text);
        }
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
