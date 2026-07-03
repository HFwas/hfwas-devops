package com.hfwas.devops.pm.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.field.mapper.FieldDefinitionMapper;
import com.hfwas.devops.pm.field.mapper.FieldOptionMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldDefinitionService {

    private final FieldDefinitionMapper fieldDefinitionMapper;
    private final FieldOptionMapper fieldOptionMapper;

    public List<FieldDefinition> listByProjectAndType(Long projectId, String typeCode) {
        List<FieldDefinition> systemFields = systemFieldDefinitions();
        List<FieldDefinition> custom = fieldDefinitionMapper.selectList(
                Wrappers.<FieldDefinition>lambdaQuery()
                        .and(w -> w.eq(FieldDefinition::getProjectId, projectId).or().isNull(FieldDefinition::getProjectId))
                        .eq(FieldDefinition::getDelFlag, 0)
                        .orderByAsc(FieldDefinition::getSortOrder)
        );
        List<FieldDefinition> merged = new ArrayList<>(systemFields);
        for (FieldDefinition def : custom) {
            if (appliesToType(def, typeCode)) {
                merged.add(def);
            }
        }
        merged.sort(Comparator.comparingInt(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()));
        return merged;
    }

    private boolean appliesToType(FieldDefinition def, String typeCode) {
        if (typeCode == null || def.getApplicableTypes() == null || def.getApplicableTypes().isEmpty()) {
            return true;
        }
        return def.getApplicableTypes().contains(typeCode);
    }

    @Transactional
    public Long save(FieldDefinition definition, List<FieldOption> options) {
        if (definition.getId() == null) {
            fieldDefinitionMapper.insert(definition);
        } else {
            fieldDefinitionMapper.updateById(definition);
            fieldOptionMapper.delete(Wrappers.<FieldOption>lambdaQuery().eq(FieldOption::getFieldId, definition.getId()));
        }
        if (options != null) {
            for (FieldOption option : options) {
                option.setFieldId(definition.getId());
                fieldOptionMapper.insert(option);
            }
        }
        return definition.getId();
    }

    public List<FieldOption> listOptions(Long fieldId) {
        return fieldOptionMapper.selectList(Wrappers.<FieldOption>lambdaQuery()
                .eq(FieldOption::getFieldId, fieldId)
                .orderByAsc(FieldOption::getSortOrder));
    }

    private List<FieldDefinition> systemFieldDefinitions() {
        List<FieldDefinition> list = new ArrayList<>();
        list.add(system("title", "标题", "TEXT", 1));
        list.add(system("status", "状态", "STATUS", 2));
        list.add(system("priority", "优先级", "PRIORITY", 3));
        list.add(system("assignee_id", "负责人", "USER", 4));
        list.add(system("type_code", "类型", "SELECT", 5));
        return list;
    }

    private FieldDefinition system(String key, String name, String type, int order) {
        FieldDefinition def = new FieldDefinition();
        def.setFieldKey(key);
        def.setFieldName(name);
        def.setFieldType(type);
        def.setSortOrder(order);
        def.setSystemFlag(1);
        def.setScope("system");
        return def;
    }
}
