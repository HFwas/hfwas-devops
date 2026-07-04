package com.hfwas.devops.pm.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.pm.common.IdUtils;
import com.hfwas.devops.pm.field.entity.PmTypeFieldLayout;
import com.hfwas.devops.pm.field.mapper.PmTypeFieldLayoutMapper;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
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
        return config;
    }

    public TypeFieldLayoutConfig resolveLayout(Long projectId, String typeCode, List<FieldDefinition> fields) {
        TypeFieldLayoutConfig saved = getLayout(projectId, typeCode);
        return saved != null ? saved : defaultLayout(fields);
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
        PmTypeFieldLayout existing = findLayoutRow(projectId, typeCode);
        if (existing == null) {
            PmTypeFieldLayout layout = new PmTypeFieldLayout();
            layout.setProjectId(projectId);
            layout.setTypeCode(typeCode);
            layout.setLayoutConfig(config);
            layoutMapper.insert(layout);
        } else {
            existing.setLayoutConfig(config);
            layoutMapper.updateById(existing);
        }
    }

    @Transactional
    public void removeFieldKey(Long projectId, String typeCode, String fieldKey, List<FieldDefinition> fields) {
        TypeFieldLayoutConfig layout = copyConfig(resolveLayout(projectId, typeCode, fields));
        layout.getListFields().remove(fieldKey);
        layout.getSearchFields().remove(fieldKey);
        layout.getCreateFields().remove(fieldKey);
        saveLayout(projectId, typeCode, layout);
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
