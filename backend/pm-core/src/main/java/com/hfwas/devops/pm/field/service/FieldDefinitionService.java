package com.hfwas.devops.pm.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.common.IdUtils;
import com.hfwas.devops.pm.field.mapper.FieldDefinitionMapper;
import com.hfwas.devops.pm.field.mapper.FieldOptionMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.FieldOption;
import com.hfwas.devops.pm.field.model.FieldOptionSource;
import com.hfwas.devops.pm.field.model.FieldRemoteOptionsConfig;
import com.hfwas.devops.pm.field.model.ResolvedFieldOption;
import com.hfwas.devops.pm.field.model.RemoteOptionFetchResult;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FieldDefinitionService {

    private final FieldDefinitionMapper fieldDefinitionMapper;
    private final FieldOptionMapper fieldOptionMapper;
    private final FieldLayoutService fieldLayoutService;
    private final FieldOptionRemoteService fieldOptionRemoteService;
    private final FieldOptionConfigHelper fieldOptionConfigHelper;

    public List<FieldDefinition> listByProjectAndType(Long projectId, String typeCode) {
        List<FieldDefinition> merged = listRawByProjectAndType(projectId, typeCode);
        TypeFieldLayoutConfig layout = fieldLayoutService.resolveLayout(projectId, typeCode, merged);
        fieldLayoutService.applyLayout(merged, layout);
        return merged;
    }

    public List<FieldDefinition> listRawByProjectAndType(Long projectId, String typeCode) {
        List<FieldDefinition> systemFields = systemFieldDefinitions();
        List<FieldDefinition> custom = fieldDefinitionMapper.selectList(
                Wrappers.<FieldDefinition>lambdaQuery()
                        .isNotNull(FieldDefinition::getProjectId)
                        .eq(FieldDefinition::getDelFlag, 0)
                        .orderByAsc(FieldDefinition::getSortOrder)
        );
        List<FieldDefinition> merged = new ArrayList<>(systemFields);
        for (FieldDefinition def : custom) {
            if (IdUtils.sameId(def.getProjectId(), projectId) && appliesToType(def, typeCode)) {
                merged.add(def);
            }
        }
        merged.sort(Comparator.comparingInt(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()));
        return merged;
    }

    private boolean appliesToType(FieldDefinition def, String typeCode) {
        if (typeCode == null) {
            return true;
        }
        if (def.getApplicableTypes() == null || def.getApplicableTypes().isEmpty()) {
            return def.getProjectId() == null;
        }
        return def.getApplicableTypes().contains(typeCode);
    }

    @Transactional
    public Long save(FieldDefinition definition, List<FieldOption> options) {
        if (StringUtils.isBlank(definition.getFieldKey()) || StringUtils.isBlank(definition.getFieldName())) {
            throw new IllegalArgumentException("字段编码和名称不能为空");
        }
        if (definition.getProjectId() != null
                && (definition.getApplicableTypes() == null || definition.getApplicableTypes().isEmpty())) {
            throw new IllegalArgumentException("项目字段必须指定适用的事项类型");
        }
        fieldOptionConfigHelper.validateSelectOptions(definition, options);
        if (FieldOptionConfigHelper.isSelectType(definition.getFieldType())
                && FieldOptionSource.REMOTE.equals(fieldOptionConfigHelper.optionSource(definition))) {
            options = null;
        }
        if (definition.getId() == null) {
            fieldDefinitionMapper.insert(definition);
        } else {
            fieldDefinitionMapper.updateById(definition);
            fieldOptionMapper.delete(Wrappers.<FieldOption>lambdaQuery().eq(FieldOption::getFieldId, definition.getId()));
        }
        fieldOptionRemoteService.invalidateCache(definition.getId());
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

    public List<ResolvedFieldOption> resolveOptions(Long fieldId) {
        FieldDefinition def = fieldDefinitionMapper.selectById(fieldId);
        if (def == null) {
            throw new IllegalArgumentException("字段不存在或已删除");
        }
        return resolveOptions(def);
    }

    public List<ResolvedFieldOption> resolveOptions(FieldDefinition def) {
        if (!FieldOptionConfigHelper.isSelectType(def.getFieldType())) {
            return List.of();
        }
        if (FieldOptionSource.REMOTE.equals(fieldOptionConfigHelper.optionSource(def))) {
            FieldRemoteOptionsConfig remote = fieldOptionConfigHelper.remoteConfig(def);
            RemoteOptionFetchResult result = fieldOptionRemoteService.fetch(remote, def.getId());
            if (!result.isSuccess()) {
                throw new IllegalStateException(StringUtils.defaultIfBlank(result.getMessage(), "远程选项加载失败"));
            }
            return result.getOptions() != null ? result.getOptions() : List.of();
        }
        List<FieldOption> dbOptions = listOptions(def.getId());
        if (!dbOptions.isEmpty()) {
            return dbOptions.stream()
                    .filter(o -> StringUtils.isNotBlank(o.getOptionKey()) && StringUtils.isNotBlank(o.getOptionLabel()))
                    .map(o -> new ResolvedFieldOption(o.getOptionKey(), o.getOptionLabel()))
                    .toList();
        }
        List<Map<String, String>> cfgOptions = fieldOptionConfigHelper.staticOptionsFromConfig(def);
        List<ResolvedFieldOption> resolved = new ArrayList<>();
        for (Map<String, String> item : cfgOptions) {
            String value = item.get("value");
            String label = item.get("label");
            if (StringUtils.isNotBlank(value) && StringUtils.isNotBlank(label)) {
                resolved.add(new ResolvedFieldOption(value, label));
            }
        }
        return resolved;
    }

    public RemoteOptionFetchResult previewRemoteOptions(FieldRemoteOptionsConfig config) {
        return fieldOptionRemoteService.fetch(config, null);
    }

    public List<FieldDefinition> listCatalogByProject(Long projectId) {
        List<FieldDefinition> system = systemFieldDefinitions();
        List<FieldDefinition> custom = fieldDefinitionMapper.selectList(
                Wrappers.<FieldDefinition>lambdaQuery()
                        .isNotNull(FieldDefinition::getProjectId)
                        .eq(FieldDefinition::getDelFlag, 0)
                        .orderByAsc(FieldDefinition::getSortOrder)
        );
        List<FieldDefinition> catalog = new ArrayList<>(system);
        for (FieldDefinition def : custom) {
            if (!IdUtils.sameId(def.getProjectId(), projectId)) {
                continue;
            }
            if (StringUtils.isNotBlank(def.getFieldKey()) && StringUtils.isNotBlank(def.getFieldName())) {
                catalog.add(def);
            }
        }
        return catalog;
    }

    public FieldDefinition getById(Long id) {
        return fieldDefinitionMapper.selectById(id);
    }

    @Transactional
    public void deleteField(Long id) {
        FieldDefinition def = fieldDefinitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("字段不存在或已删除");
        }
        if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
            throw new IllegalArgumentException("系统字段不可删除");
        }
        fieldOptionMapper.delete(Wrappers.<FieldOption>lambdaQuery().eq(FieldOption::getFieldId, id));
        fieldOptionRemoteService.invalidateCache(id);
        fieldDefinitionMapper.deleteById(id);
    }

    @Transactional
    public void removeFromType(Long projectId, Long fieldId, String typeCode) {
        FieldDefinition def = fieldDefinitionMapper.selectById(fieldId);
        if (def == null) {
            throw new IllegalArgumentException("字段不存在或已删除");
        }
        if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
            throw new IllegalArgumentException("系统字段不可修改绑定");
        }
        List<FieldDefinition> currentFields = listRawByProjectAndType(projectId, typeCode);
        boolean bound = currentFields.stream().anyMatch(f ->
                Objects.equals(f.getId(), fieldId)
                        || (def.getFieldKey() != null && def.getFieldKey().equals(f.getFieldKey())));
        if (!bound) {
            throw new IllegalArgumentException("该字段未绑定到当前事项类型");
        }
        List<String> types = def.getApplicableTypes();
        if (types == null || types.isEmpty() || (types.size() == 1 && types.contains(typeCode))) {
            deleteField(fieldId);
        } else if (types.contains(typeCode)) {
            List<String> updated = new ArrayList<>(types);
            updated.remove(typeCode);
            def.setApplicableTypes(updated);
            fieldDefinitionMapper.updateById(def);
        }
        fieldLayoutService.removeFieldKey(projectId, typeCode, def.getFieldKey(), currentFields);
    }

    @Transactional
    public void addToType(Long projectId, Long fieldId, String typeCode) {
        FieldDefinition def = fieldDefinitionMapper.selectById(fieldId);
        if (def == null) {
            throw new IllegalArgumentException("字段不存在或已删除");
        }
        if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
            throw new IllegalArgumentException("系统字段不可修改绑定");
        }
        if (def.getProjectId() != null && !IdUtils.sameId(def.getProjectId(), projectId)) {
            throw new IllegalArgumentException("字段不属于当前项目");
        }
        List<String> types = def.getApplicableTypes();
        if (types == null) {
            types = new ArrayList<>();
        } else {
            types = new ArrayList<>(types);
        }
        if (!types.contains(typeCode)) {
            types.add(typeCode);
            def.setApplicableTypes(types);
            fieldDefinitionMapper.updateById(def);
        }
        List<FieldDefinition> fields = listRawByProjectAndType(projectId, typeCode);
        if (fields.stream().noneMatch(f -> def.getFieldKey().equals(f.getFieldKey()))) {
            fields = new ArrayList<>(fields);
            fields.add(def);
        }
        fieldLayoutService.ensureFieldKey(projectId, typeCode, def.getFieldKey(), fields);
    }

    public List<FieldDefinition> listAvailableForType(Long projectId, String typeCode) {
        List<FieldDefinition> catalog = listCatalogByProject(projectId);
        List<FieldDefinition> current = listRawByProjectAndType(projectId, typeCode);
        Set<String> currentKeys = current.stream()
                .map(FieldDefinition::getFieldKey)
                .filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
        List<FieldDefinition> available = new ArrayList<>();
        for (FieldDefinition def : catalog) {
            if (def.getSystemFlag() != null && def.getSystemFlag() == 1) {
                continue;
            }
            if (def.getId() == null) {
                continue;
            }
            if (currentKeys.contains(def.getFieldKey())) {
                continue;
            }
            available.add(def);
        }
        return available;
    }

    private List<FieldDefinition> systemFieldDefinitions() {
        List<FieldDefinition> list = new ArrayList<>();
        list.add(system("title", "标题", "TEXT", 1));
        list.add(system("description", "描述", "MARKDOWN", 2));
        list.add(system("status", "状态", "STATUS", 3));
        list.add(system("priority", "优先级", "PRIORITY", 4));
        list.add(system("assignee_id", "负责人", "USER", 5));
        list.add(system("module_id", "功能模块", "MODULE", 6));
        list.add(system("type_code", "类型", "SELECT", 7));
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
