package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.workitem.model.TransitionFieldMetaVO;
import com.hfwas.devops.pm.workitem.model.TransitionFieldOptionVO;
import com.hfwas.devops.pm.workitem.model.TransitionMetaVO;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionMetaVO;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionPresetVO;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionType;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorType;
import com.hfwas.devops.pm.workitem.model.TransitionValidatorVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransitionPostFunctionMetaService {

    private static final Set<String> SETTABLE_SYSTEM_FIELDS = Set.of(
            "priority", "assignee_id", "reporter_id", "module_id"
    );

    private final FieldDefinitionService fieldDefinitionService;
    private final StatusDefinitionService statusDefinitionService;

    public TransitionPostFunctionMetaVO getMeta(Long projectId, String typeCode) {
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        List<TransitionFieldMetaVO> fields = buildFieldMeta(projectId, typeCode);
        TransitionPostFunctionMetaVO vo = new TransitionPostFunctionMetaVO();
        vo.setFields(fields);
        vo.setPresets(buildPresets(fields));
        return vo;
    }

    public TransitionMetaVO getTransitionMeta(Long projectId, String typeCode, String fromStatus, String transitionId) {
        if (projectId == null || StringUtils.isBlank(typeCode) || StringUtils.isBlank(transitionId)) {
            throw new IllegalArgumentException("projectId、typeCode、transitionId 不能为空");
        }
        TransitionVO transition = statusDefinitionService.findTransition(
                projectId, typeCode, fromStatus, transitionId);
        List<TransitionValidatorVO> validators = statusDefinitionService.resolveValidators(
                projectId, typeCode, fromStatus, transitionId);
        List<TransitionFieldMetaVO> allFields = buildFieldMeta(projectId, typeCode);
        Map<String, TransitionFieldMetaVO> byKey = allFields.stream()
                .collect(Collectors.toMap(TransitionFieldMetaVO::getFieldKey, f -> f, (a, b) -> a, LinkedHashMap::new));
        LinkedHashSet<String> requiredKeys = new LinkedHashSet<>();
        for (TransitionValidatorVO validator : validators) {
            if (validator == null || validator.getFieldKeys() == null) {
                continue;
            }
            if (!TransitionValidatorType.REQUIRED_FIELDS.equals(
                    validator.getType() != null ? validator.getType().trim() : null)) {
                continue;
            }
            for (String key : validator.getFieldKeys()) {
                if (StringUtils.isNotBlank(key) && !"status".equals(key.trim())) {
                    requiredKeys.add(key.trim());
                }
            }
        }
        List<TransitionFieldMetaVO> requiredFields = new ArrayList<>();
        for (String key : requiredKeys) {
            TransitionFieldMetaVO field = byKey.get(key);
            if (field != null) {
                requiredFields.add(field);
            } else {
                TransitionFieldMetaVO fallback = new TransitionFieldMetaVO();
                fallback.setFieldKey(key);
                fallback.setFieldName(key);
                fallback.setFieldType("TEXT");
                requiredFields.add(fallback);
            }
        }
        TransitionMetaVO vo = new TransitionMetaVO();
        vo.setTransitionId(transition.getId());
        vo.setFromStatus(fromStatus);
        vo.setToStatus(transition.getToStatus());
        vo.setName(transition.getName());
        vo.setValidators(validators);
        vo.setRequiredFields(requiredFields);
        return vo;
    }

    private List<TransitionPostFunctionPresetVO> buildPresets(List<TransitionFieldMetaVO> fields) {
        List<TransitionPostFunctionPresetVO> presets = new ArrayList<>();
        presets.add(preset("notify_assignee", "通知负责人", "流转成功后提醒当前负责人", "bell",
                TransitionPostFunctionType.NOTIFY_ASSIGNEE, null, null));
        presets.add(preset("notify_user", "通知指定成员", "选择一位成员并发送站内信", "user",
                TransitionPostFunctionType.NOTIFY_USER, null, null));
        presets.add(preset("webhook", "发送群通知", "推送到租户已启用的钉钉/飞书", "webhook",
                TransitionPostFunctionType.WEBHOOK, null, null));

        TransitionFieldMetaVO priority = findField(fields, "priority");
        if (priority != null) {
            for (TransitionFieldOptionVO option : priority.getOptions()) {
                presets.add(fieldPreset(
                        "priority_" + option.getValue(),
                        "设为「" + option.getLabel() + "」优先级",
                        "自动更新事项优先级",
                        "priority",
                        option.getValue()));
            }
        }
        for (TransitionFieldMetaVO field : fields) {
            if ("priority".equals(field.getFieldKey())) {
                continue;
            }
            if (field.getSystemFlag() != null && field.getSystemFlag() == 1
                    && !SETTABLE_SYSTEM_FIELDS.contains(field.getFieldKey())) {
                continue;
            }
            if ("STATUS".equals(field.getFieldType())) {
                continue;
            }
            presets.add(templatePreset(field));
        }
        return presets;
    }

    private List<TransitionFieldMetaVO> buildFieldMeta(Long projectId, String typeCode) {
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(projectId, typeCode);
        List<TransitionFieldMetaVO> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FieldDefinition def : definitions) {
            if (def.getFieldKey() == null || !seen.add(def.getFieldKey())) {
                continue;
            }
            if (def.getSystemFlag() != null && def.getSystemFlag() == 1
                    && !SETTABLE_SYSTEM_FIELDS.contains(def.getFieldKey())) {
                continue;
            }
            if ("status".equals(def.getFieldKey()) || "STATUS".equals(def.getFieldType())) {
                continue;
            }
            TransitionFieldMetaVO field = new TransitionFieldMetaVO();
            field.setFieldKey(def.getFieldKey());
            field.setFieldName(def.getFieldName());
            field.setFieldType(def.getFieldType());
            field.setSystemFlag(def.getSystemFlag());
            field.setOptions(resolveOptions(def));
            fields.add(field);
        }
        return fields;
    }

    private List<TransitionFieldOptionVO> resolveOptions(FieldDefinition def) {
        if ("PRIORITY".equals(def.getFieldType()) || "priority".equals(def.getFieldKey())) {
            return List.of(
                    option("低", "low"),
                    option("中", "medium"),
                    option("高", "high"),
                    option("紧急", "critical")
            );
        }
        if ("BOOLEAN".equals(def.getFieldType())) {
            return List.of(option("是", "true"), option("否", "false"));
        }
        if ("SELECT".equals(def.getFieldType()) || "MULTI_SELECT".equals(def.getFieldType())) {
            List<FieldOption> options = fieldDefinitionService.listOptions(def.getId());
            if (options == null || options.isEmpty()) {
                return List.of();
            }
            return options.stream()
                    .map(o -> option(
                            StringUtils.defaultIfBlank(o.getOptionLabel(), o.getOptionKey()),
                            o.getOptionKey()))
                    .toList();
        }
        return List.of();
    }

    private TransitionPostFunctionPresetVO preset(String id, String label, String description, String icon,
                                                  String type, String fieldKey, Object value) {
        TransitionPostFunctionPresetVO preset = new TransitionPostFunctionPresetVO();
        preset.setId(id);
        preset.setLabel(label);
        preset.setDescription(description);
        preset.setIcon(icon);
        preset.setType(type);
        preset.setFieldKey(fieldKey);
        preset.setValue(value);
        preset.setKind("preset");
        return preset;
    }

    private TransitionPostFunctionPresetVO fieldPreset(String id, String label, String description,
                                                       String fieldKey, Object value) {
        TransitionPostFunctionPresetVO preset = preset(id, label, description, "field",
                TransitionPostFunctionType.SET_FIELD, fieldKey, value);
        preset.setKind("preset");
        return preset;
    }

    private TransitionPostFunctionPresetVO templatePreset(TransitionFieldMetaVO field) {
        TransitionPostFunctionPresetVO preset = new TransitionPostFunctionPresetVO();
        preset.setId("field_" + field.getFieldKey());
        preset.setLabel("设置「" + field.getFieldName() + "」");
        preset.setDescription("流转后自动修改该字段");
        preset.setIcon("field");
        preset.setType(TransitionPostFunctionType.SET_FIELD);
        preset.setFieldKey(field.getFieldKey());
        preset.setKind("template");
        return preset;
    }

    private TransitionFieldMetaVO findField(List<TransitionFieldMetaVO> fields, String fieldKey) {
        return fields.stream()
                .filter(f -> fieldKey.equals(f.getFieldKey()))
                .findFirst()
                .orElse(null);
    }

    private TransitionFieldOptionVO option(String label, String value) {
        TransitionFieldOptionVO vo = new TransitionFieldOptionVO();
        vo.setLabel(label);
        vo.setValue(value);
        return vo;
    }
}
