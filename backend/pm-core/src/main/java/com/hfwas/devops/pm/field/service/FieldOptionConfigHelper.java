package com.hfwas.devops.pm.field.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOptionSource;
import com.hfwas.devops.pm.field.model.FieldRemoteOptionsConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FieldOptionConfigHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String optionSource(FieldDefinition definition) {
        if (definition == null || definition.getConfig() == null) {
            return FieldOptionSource.STATIC;
        }
        Object source = definition.getConfig().get("optionSource");
        if (source == null) {
            return FieldOptionSource.STATIC;
        }
        String s = String.valueOf(source);
        return FieldOptionSource.REMOTE.equals(s) ? FieldOptionSource.REMOTE : FieldOptionSource.STATIC;
    }

    public FieldRemoteOptionsConfig remoteConfig(FieldDefinition definition) {
        if (definition == null || definition.getConfig() == null) {
            return null;
        }
        Object raw = definition.getConfig().get("remoteOptions");
        if (raw == null) {
            return null;
        }
        return objectMapper.convertValue(raw, FieldRemoteOptionsConfig.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> staticOptionsFromConfig(FieldDefinition definition) {
        if (definition == null || definition.getConfig() == null) {
            return List.of();
        }
        Object raw = definition.getConfig().get("options");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, String>) item)
                .toList();
    }

    public void validateSelectOptions(FieldDefinition definition, List<com.hfwas.devops.pm.field.model.FieldOption> options) {
        if (definition == null || !isSelectType(definition.getFieldType())) {
            return;
        }
        String source = optionSource(definition);
        if (FieldOptionSource.REMOTE.equals(source)) {
            FieldRemoteOptionsConfig remote = remoteConfig(definition);
            if (remote == null || StringUtils.isBlank(remote.getUrl())) {
                throw new IllegalArgumentException("远程选项接口地址不能为空");
            }
            if (StringUtils.isBlank(remote.getValueField()) || StringUtils.isBlank(remote.getLabelField())) {
                throw new IllegalArgumentException("远程选项的值字段与显示字段不能为空");
            }
            return;
        }
        boolean hasDbOptions = options != null && options.stream()
                .anyMatch(o -> StringUtils.isNotBlank(o.getOptionKey()) && StringUtils.isNotBlank(o.getOptionLabel()));
        if (hasDbOptions) {
            return;
        }
        List<Map<String, String>> cfgOptions = staticOptionsFromConfig(definition);
        boolean hasCfg = cfgOptions.stream()
                .anyMatch(o -> StringUtils.isNotBlank(o.get("value")) && StringUtils.isNotBlank(o.get("label")));
        if (!hasCfg) {
            throw new IllegalArgumentException("静态选项列表不能为空");
        }
    }

    public static boolean isSelectType(String fieldType) {
        return "SELECT".equals(fieldType) || "MULTI_SELECT".equals(fieldType);
    }
}
