package com.hfwas.devops.pm.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.common.IdUtils;
import com.hfwas.devops.pm.field.DetailTabCatalog;
import com.hfwas.devops.pm.field.FeatureCatalog;
import com.hfwas.devops.pm.field.mapper.FieldDefinitionMapper;
import com.hfwas.devops.pm.field.model.*;
import com.hfwas.devops.pm.meta.PmWorkItemType;
import com.hfwas.devops.pm.meta.PmWorkItemTypeMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldSchemeService {

    public static final Set<String> SYSTEM_FIELD_KEYS = Set.of(
            "title", "description", "status", "priority", "assignee_id", "module_id", "type_code");

    private final FieldDefinitionService fieldDefinitionService;
    private final FieldLayoutService fieldLayoutService;
    private final FieldDefinitionMapper fieldDefinitionMapper;
    private final PmWorkItemTypeMapper workItemTypeMapper;

    public TypeFieldSchemeExport exportTypeScheme(Long projectId, String typeCode) {
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        List<FieldDefinition> fields = fieldDefinitionService.listRawByProjectAndType(projectId, typeCode);
        TypeFieldLayoutConfig layout = fieldLayoutService.resolveLayout(projectId, typeCode, fields);

        TypeFieldSchemeExport export = new TypeFieldSchemeExport();
        export.setTypeCode(typeCode);
        export.setTypeName(resolveTypeName(typeCode));
        export.setLayout(copyLayout(layout));
        export.setCustomFields(fields.stream()
                .filter(f -> f.getSystemFlag() == null || f.getSystemFlag() != 1)
                .map(this::toExportedField)
                .toList());
        export.setExportedAt(LocalDateTime.now());
        return export;
    }

    public ProjectFieldSchemeExport exportProjectSchemes(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        List<PmWorkItemType> types = workItemTypeMapper.selectList(
                Wrappers.<PmWorkItemType>lambdaQuery()
                        .eq(PmWorkItemType::getEnabled, 1)
                        .orderByAsc(PmWorkItemType::getSortOrder));
        ProjectFieldSchemeExport export = new ProjectFieldSchemeExport();
        for (PmWorkItemType type : types) {
            export.getSchemes().add(exportTypeScheme(projectId, type.getCode()));
        }
        export.setExportedAt(LocalDateTime.now());
        return export;
    }

    @Transactional
    public FieldSchemeImportResult importTypeScheme(Long projectId, String typeCode, TypeFieldSchemeExport payload,
                                                    FieldSchemeImportMode mode) {
        validateTypePayload(payload);
        if (projectId == null || StringUtils.isBlank(typeCode)) {
            throw new IllegalArgumentException("projectId 与 typeCode 不能为空");
        }
        FieldSchemeImportResult result = new FieldSchemeImportResult();
        Set<String> importedKeys = new HashSet<>();

        if (payload.getCustomFields() != null) {
            for (ExportedFieldDefinition exported : payload.getCustomFields()) {
                if (exported == null || StringUtils.isBlank(exported.getFieldKey())) {
                    result.getWarnings().add("跳过无效字段：编码为空");
                    result.setFieldsSkipped(result.getFieldsSkipped() + 1);
                    continue;
                }
                String key = exported.getFieldKey().trim();
                if (SYSTEM_FIELD_KEYS.contains(key)) {
                    result.getWarnings().add("跳过系统字段: " + key);
                    result.setFieldsSkipped(result.getFieldsSkipped() + 1);
                    continue;
                }
                importedKeys.add(key);
                upsertCustomField(projectId, typeCode, exported, result);
            }
        }

        if (mode == FieldSchemeImportMode.REPLACE) {
            removeCustomFieldsNotInImport(projectId, typeCode, importedKeys, result);
        }

        TypeFieldLayoutConfig layout = sanitizeLayout(payload.getLayout(), importedKeys);
        fieldLayoutService.saveLayout(projectId, typeCode, layout);
        result.setLayoutApplied(true);
        return result;
    }

    @Transactional
    public List<FieldSchemeImportResult> importProjectSchemes(Long projectId, ProjectFieldSchemeExport payload,
                                                              FieldSchemeImportMode mode) {
        validateProjectPayload(payload);
        List<FieldSchemeImportResult> results = new ArrayList<>();
        for (TypeFieldSchemeExport scheme : payload.getSchemes()) {
            if (scheme == null || StringUtils.isBlank(scheme.getTypeCode())) {
                continue;
            }
            results.add(importTypeScheme(projectId, scheme.getTypeCode(), scheme, mode));
        }
        return results;
    }

    public FieldSchemeImportPreview previewTypeImport(Long projectId, String typeCode, TypeFieldSchemeExport payload) {
        validateTypePayload(payload);
        FieldSchemeImportPreview preview = new FieldSchemeImportPreview();
        preview.setTypeCode(typeCode);
        preview.setSourceTypeCode(payload.getTypeCode());
        preview.setCustomFieldCount(payload.getCustomFields() != null ? payload.getCustomFields().size() : 0);
        preview.setLayoutFieldCount(countLayoutFields(payload.getLayout()));

        Set<String> existingKeys = fieldDefinitionService.listRawByProjectAndType(projectId, typeCode).stream()
                .filter(f -> f.getSystemFlag() == null || f.getSystemFlag() != 1)
                .map(FieldDefinition::getFieldKey)
                .collect(Collectors.toSet());

        int create = 0;
        int update = 0;
        if (payload.getCustomFields() != null) {
            for (ExportedFieldDefinition exported : payload.getCustomFields()) {
                if (exported == null || StringUtils.isBlank(exported.getFieldKey())) {
                    continue;
                }
                String key = exported.getFieldKey().trim();
                if (SYSTEM_FIELD_KEYS.contains(key)) {
                    continue;
                }
                if (existingKeys.contains(key) || findCustomByKey(projectId, key) != null) {
                    update++;
                } else {
                    create++;
                }
            }
        }
        preview.setFieldsToCreate(create);
        preview.setFieldsToUpdate(update);
        if (!StringUtils.equals(typeCode, payload.getTypeCode())) {
            preview.getWarnings().add("导入源事项类型为「" + payload.getTypeCode() + "」，将应用到当前「" + typeCode + "」");
        }
        return preview;
    }

    private void upsertCustomField(Long projectId, String typeCode, ExportedFieldDefinition exported,
                                   FieldSchemeImportResult result) {
        FieldDefinition existing = findCustomByKey(projectId, exported.getFieldKey().trim());
        FieldDefinition def = existing != null ? existing : new FieldDefinition();
        if (existing == null) {
            def.setProjectId(projectId);
            def.setScope("project");
            def.setSystemFlag(0);
            def.setApplicableTypes(new ArrayList<>(List.of(typeCode)));
        } else {
            ensureTypeBinding(def, typeCode);
        }
        def.setFieldKey(exported.getFieldKey().trim());
        def.setFieldName(StringUtils.defaultIfBlank(exported.getFieldName(), exported.getFieldKey()).trim());
        def.setFieldType(StringUtils.defaultIfBlank(exported.getFieldType(), "TEXT"));
        def.setRequiredFlag(exported.getRequiredFlag() != null ? exported.getRequiredFlag() : 0);
        def.setSortOrder(exported.getSortOrder() != null ? exported.getSortOrder() : 100);
        def.setConfig(exported.getConfig());

        List<FieldOption> options = toFieldOptions(exported.getOptions());
        fieldDefinitionService.save(def, options);
        if (existing == null) {
            result.setFieldsCreated(result.getFieldsCreated() + 1);
        } else {
            result.setFieldsUpdated(result.getFieldsUpdated() + 1);
        }
    }

    private void removeCustomFieldsNotInImport(Long projectId, String typeCode, Set<String> importedKeys,
                                               FieldSchemeImportResult result) {
        List<FieldDefinition> current = fieldDefinitionService.listRawByProjectAndType(projectId, typeCode);
        for (FieldDefinition field : current) {
            if (field.getSystemFlag() != null && field.getSystemFlag() == 1) {
                continue;
            }
            if (field.getId() == null || importedKeys.contains(field.getFieldKey())) {
                continue;
            }
            fieldDefinitionService.removeFromType(projectId, field.getId(), typeCode);
            result.getWarnings().add("已移除未包含在导入包中的字段: " + field.getFieldKey());
        }
    }

    private FieldDefinition findCustomByKey(Long projectId, String fieldKey) {
        List<FieldDefinition> custom = fieldDefinitionMapper.selectList(
                Wrappers.<FieldDefinition>lambdaQuery()
                        .isNotNull(FieldDefinition::getProjectId)
                        .eq(FieldDefinition::getDelFlag, 0)
                        .eq(FieldDefinition::getFieldKey, fieldKey));
        for (FieldDefinition def : custom) {
            if (IdUtils.sameId(def.getProjectId(), projectId)
                    && (def.getSystemFlag() == null || def.getSystemFlag() != 1)) {
                return def;
            }
        }
        return null;
    }

    private void ensureTypeBinding(FieldDefinition def, String typeCode) {
        List<String> types = def.getApplicableTypes();
        if (types == null) {
            types = new ArrayList<>();
        } else {
            types = new ArrayList<>(types);
        }
        if (!types.contains(typeCode)) {
            types.add(typeCode);
            def.setApplicableTypes(types);
        }
    }

    private ExportedFieldDefinition toExportedField(FieldDefinition field) {
        ExportedFieldDefinition exported = new ExportedFieldDefinition();
        exported.setFieldKey(field.getFieldKey());
        exported.setFieldName(field.getFieldName());
        exported.setFieldType(field.getFieldType());
        exported.setRequiredFlag(field.getRequiredFlag());
        exported.setSortOrder(field.getSortOrder());
        exported.setConfig(field.getConfig() != null ? new LinkedHashMap<>(field.getConfig()) : null);
        if (field.getId() != null) {
            exported.setOptions(fieldDefinitionService.listOptions(field.getId()).stream()
                    .map(o -> {
                        ExportedFieldOption opt = new ExportedFieldOption();
                        opt.setOptionKey(o.getOptionKey());
                        opt.setOptionLabel(o.getOptionLabel());
                        opt.setSortOrder(o.getSortOrder());
                        return opt;
                    })
                    .toList());
        }
        return exported;
    }

    private List<FieldOption> toFieldOptions(List<ExportedFieldOption> exported) {
        if (exported == null || exported.isEmpty()) {
            return null;
        }
        List<FieldOption> options = new ArrayList<>();
        int order = 1;
        for (ExportedFieldOption item : exported) {
            if (item == null || StringUtils.isBlank(item.getOptionKey()) || StringUtils.isBlank(item.getOptionLabel())) {
                continue;
            }
            FieldOption opt = new FieldOption();
            opt.setOptionKey(item.getOptionKey().trim());
            opt.setOptionLabel(item.getOptionLabel().trim());
            opt.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            options.add(opt);
            order++;
        }
        return options.isEmpty() ? null : options;
    }

    private TypeFieldLayoutConfig sanitizeLayout(TypeFieldLayoutConfig layout, Set<String> customKeys) {
        TypeFieldLayoutConfig sanitized = copyLayout(layout != null ? layout : new TypeFieldLayoutConfig());
        sanitized.setListFields(filterLayoutKeys(sanitized.getListFields(), customKeys));
        sanitized.setSearchFields(filterLayoutKeys(sanitized.getSearchFields(), customKeys));
        sanitized.setCreateFields(filterLayoutKeys(sanitized.getCreateFields(), customKeys));
        sanitized.setDetailTabs(DetailTabCatalog.sanitize(sanitized.getDetailTabs()));
        LinkedHashSet<String> featureAllowed = new LinkedHashSet<>();
        featureAllowed.addAll(SYSTEM_FIELD_KEYS);
        featureAllowed.add("itemKey");
        featureAllowed.addAll(customKeys);
        sanitized.setFeatures(FeatureCatalog.sanitize(sanitized.getFeatures(), featureAllowed));
        return sanitized;
    }

    private List<String> filterLayoutKeys(List<String> keys, Set<String> customKeys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.addAll(SYSTEM_FIELD_KEYS);
        allowed.addAll(customKeys);
        return keys.stream()
                .filter(k -> k != null && allowed.contains(k.trim()))
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private TypeFieldLayoutConfig copyLayout(TypeFieldLayoutConfig source) {
        TypeFieldLayoutConfig copy = new TypeFieldLayoutConfig();
        copy.setListFields(source.getListFields() != null ? new ArrayList<>(source.getListFields()) : new ArrayList<>());
        copy.setSearchFields(source.getSearchFields() != null ? new ArrayList<>(source.getSearchFields()) : new ArrayList<>());
        copy.setCreateFields(source.getCreateFields() != null ? new ArrayList<>(source.getCreateFields()) : new ArrayList<>());
        copy.setDetailTabs(DetailTabCatalog.sanitize(source.getDetailTabs()));
        copy.setFeatures(FeatureCatalog.sanitize(source.getFeatures()));
        return copy;
    }

    private int countLayoutFields(TypeFieldLayoutConfig layout) {
        if (layout == null) {
            return 0;
        }
        Set<String> keys = new LinkedHashSet<>();
        if (layout.getListFields() != null) {
            keys.addAll(layout.getListFields());
        }
        if (layout.getSearchFields() != null) {
            keys.addAll(layout.getSearchFields());
        }
        if (layout.getCreateFields() != null) {
            keys.addAll(layout.getCreateFields());
        }
        return keys.size();
    }

    private String resolveTypeName(String typeCode) {
        PmWorkItemType type = workItemTypeMapper.selectOne(
                Wrappers.<PmWorkItemType>lambdaQuery().eq(PmWorkItemType::getCode, typeCode));
        return type != null ? type.getName() : typeCode;
    }

    private void validateTypePayload(TypeFieldSchemeExport payload) {
        if (payload == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        if (payload.getSchemaVersion() != TypeFieldSchemeExport.SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 schema 版本: " + payload.getSchemaVersion());
        }
        if (!TypeFieldSchemeExport.KIND.equals(payload.getKind())) {
            throw new IllegalArgumentException("文件类型不匹配，期望 " + TypeFieldSchemeExport.KIND);
        }
        if (StringUtils.isBlank(payload.getTypeCode())) {
            throw new IllegalArgumentException("导入包缺少 typeCode");
        }
    }

    private void validateProjectPayload(ProjectFieldSchemeExport payload) {
        if (payload == null) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        if (payload.getSchemaVersion() != ProjectFieldSchemeExport.SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 schema 版本: " + payload.getSchemaVersion());
        }
        if (!ProjectFieldSchemeExport.KIND.equals(payload.getKind())) {
            throw new IllegalArgumentException("文件类型不匹配，期望 " + ProjectFieldSchemeExport.KIND);
        }
        if (payload.getSchemes() == null || payload.getSchemes().isEmpty()) {
            throw new IllegalArgumentException("导入包中没有事项类型配置");
        }
    }
}
