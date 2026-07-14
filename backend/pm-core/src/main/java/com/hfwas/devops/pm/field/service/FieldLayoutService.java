package com.hfwas.devops.pm.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.common.IdUtils;
import com.hfwas.devops.pm.field.DetailTabCatalog;
import com.hfwas.devops.pm.field.FeatureCatalog;
import com.hfwas.devops.pm.field.entity.PmTypeFieldLayout;
import com.hfwas.devops.pm.field.mapper.PmTypeFieldLayoutMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import com.hfwas.devops.pm.field.model.TypeFeaturesConfig;
import com.hfwas.devops.pm.field.model.WorkItemIoFeatureConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FieldLayoutService {

    private static final Set<String> EXCLUDED_FROM_CREATE = Set.of("type_code");
    private static final List<String> DEFAULT_LIST = List.of("title", "status", "priority", "assignee_id");
    private static final List<String> DEFAULT_SEARCH = List.of("title", "status", "priority");

    private final PmTypeFieldLayoutMapper layoutMapper;

    public TypeFieldLayoutConfig getLayout(Long projectId, String typeCode) {
        PmTypeFieldLayout saved = findLayoutRow(projectId, typeCode);
        return saved != null && saved.getLayoutConfig() != null ? copyConfig(saved.getLayoutConfig()) : null;
    }

    private PmTypeFieldLayout findLayoutRow(Long projectId, String typeCode) {
        PmTypeFieldLayout exact = layoutMapper.selectOne(Wrappers.<PmTypeFieldLayout>lambdaQuery()
                .eq(PmTypeFieldLayout::getProjectId, projectId)
                .eq(PmTypeFieldLayout::getTypeCode, typeCode));
        if (exact != null) {
            return exact;
        }
        List<PmTypeFieldLayout> byType = layoutMapper.selectList(Wrappers.<PmTypeFieldLayout>lambdaQuery()
                .eq(PmTypeFieldLayout::getTypeCode, typeCode));
        for (PmTypeFieldLayout row : byType) {
            if (IdUtils.sameId(row.getProjectId(), projectId)) {
                return row;
            }
        }
        return byType.size() == 1 ? byType.get(0) : null;
    }

    private TypeFieldLayoutConfig copyConfig(TypeFieldLayoutConfig source) {
        TypeFieldLayoutConfig copy = new TypeFieldLayoutConfig();
        copy.setListFields(source.getListFields() != null ? new ArrayList<>(source.getListFields()) : new ArrayList<>());
        copy.setSearchFields(source.getSearchFields() != null ? new ArrayList<>(source.getSearchFields()) : new ArrayList<>());
        copy.setCreateFields(source.getCreateFields() != null ? new ArrayList<>(source.getCreateFields()) : new ArrayList<>());
        copy.setDetailTabs(DetailTabCatalog.sanitize(source.getDetailTabs()));
        copy.setFeatures(FeatureCatalog.sanitize(source.getFeatures()));
        return copy;
    }

    public TypeFieldLayoutConfig defaultLayout(List<FieldDefinition> fields) {
        TypeFieldLayoutConfig config = new TypeFieldLayoutConfig();
        config.setListFields(new ArrayList<>(DEFAULT_LIST));
        config.setSearchFields(new ArrayList<>(DEFAULT_SEARCH));
        List<String> createFields = new ArrayList<>();
        for (FieldDefinition field : fields) {
            if (!EXCLUDED_FROM_CREATE.contains(field.getFieldKey())) {
                createFields.add(field.getFieldKey());
            }
        }
        config.setCreateFields(createFields);
        config.setDetailTabs(new ArrayList<>(DetailTabCatalog.defaultEnabledIds()));
        config.setFeatures(FeatureCatalog.defaultFeatures());
        return config;
    }

    public TypeFieldLayoutConfig resolveLayout(Long projectId, String typeCode, List<FieldDefinition> fields) {
        TypeFieldLayoutConfig saved = getLayout(projectId, typeCode);
        if (saved == null) {
            return defaultLayout(fields);
        }
        if (saved.getDetailTabs() == null || saved.getDetailTabs().isEmpty()) {
            saved.setDetailTabs(new ArrayList<>(DetailTabCatalog.defaultEnabledIds()));
        } else {
            saved.setDetailTabs(DetailTabCatalog.sanitize(saved.getDetailTabs()));
        }
        saved.setFeatures(FeatureCatalog.sanitize(saved.getFeatures()));
        return saved;
    }

    public void applyLayout(List<FieldDefinition> fields, TypeFieldLayoutConfig layout) {
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            String key = field.getFieldKey();
            field.setShowInList(layout.getListFields().contains(key));
            field.setSearchable(layout.getSearchFields().contains(key));
            field.setShowInCreate(layout.getCreateFields().contains(key));
            field.setListOrder(layout.getListFields().indexOf(key));
            if (field.getListOrder() < 0) {
                field.setListOrder(100 + i);
            }
        }
    }

    @Transactional
    public void saveLayout(Long projectId, String typeCode, TypeFieldLayoutConfig config) {
        TypeFieldLayoutConfig toSave = config == null
                ? defaultLayout(List.of())
                : copyConfig(config);
        PmTypeFieldLayout existing = findLayoutRow(projectId, typeCode);
        if (existing == null) {
            PmTypeFieldLayout layout = new PmTypeFieldLayout();
            layout.setProjectId(projectId);
            layout.setTypeCode(typeCode);
            layout.setLayoutConfig(toSave);
            layoutMapper.insert(layout);
        } else {
            existing.setLayoutConfig(toSave);
            layoutMapper.updateById(existing);
        }
    }

    @Transactional
    public void removeFieldKey(Long projectId, String typeCode, String fieldKey, List<FieldDefinition> fields) {
        TypeFieldLayoutConfig layout = copyConfig(resolveLayout(projectId, typeCode, fields));
        layout.getListFields().remove(fieldKey);
        layout.getSearchFields().remove(fieldKey);
        layout.getCreateFields().remove(fieldKey);
        removeFeatureFieldKey(layout.getFeatures(), fieldKey);
        saveLayout(projectId, typeCode, layout);
    }

    private void removeFeatureFieldKey(TypeFeaturesConfig features, String fieldKey) {
        if (features == null || features.getWorkItemIo() == null) {
            return;
        }
        WorkItemIoFeatureConfig io = features.getWorkItemIo();
        if (io.getExportFieldKeys() != null) {
            io.getExportFieldKeys().remove(fieldKey);
        }
        if (io.getImportFieldKeys() != null) {
            io.getImportFieldKeys().remove(fieldKey);
        }
    }

    @Transactional
    public void ensureFieldKey(Long projectId, String typeCode, String fieldKey, List<FieldDefinition> fields) {
        TypeFieldLayoutConfig layout = copyConfig(resolveLayout(projectId, typeCode, fields));
        if (EXCLUDED_FROM_CREATE.contains(fieldKey)) {
            saveLayout(projectId, typeCode, layout);
            return;
        }
        if (!layout.getCreateFields().contains(fieldKey)) {
            layout.getCreateFields().add(fieldKey);
        }
        if (!layout.getListFields().contains(fieldKey)) {
            layout.getListFields().add(fieldKey);
        }
        saveLayout(projectId, typeCode, layout);
    }
}
