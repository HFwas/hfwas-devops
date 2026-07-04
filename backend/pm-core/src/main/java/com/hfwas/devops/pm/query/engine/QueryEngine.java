package com.hfwas.devops.pm.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.query.model.*;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueryEngine {

    private final PmWorkItemMapper workItemMapper;
    private final FieldDefinitionService fieldDefinitionService;
    private final FieldResolver fieldResolver;

    public IPage<PmWorkItem> execute(QuerySpec spec) {
        Map<String, FieldDefinition> customFieldMap = loadCustomFields(spec.getProjectId(), spec.getTypeCode());
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        if (spec.getProjectId() != null) {
            wrapper.eq("project_id", spec.getProjectId());
        }
        if (StringUtils.isNotBlank(spec.getTypeCode())) {
            wrapper.eq("type_code", spec.getTypeCode());
        }
        for (QueryCondition condition : spec.getConditions()) {
            fieldResolver.applyCondition(wrapper, condition, customFieldMap);
        }
        for (QueryConditionGroup group : spec.getGroups()) {
            fieldResolver.applyGroup(wrapper, group, customFieldMap);
        }
        applySort(wrapper, spec.getSort());
        Page<PmWorkItem> page = new Page<>(spec.resolvePageNo(), spec.resolvePageSize());
        return workItemMapper.selectPage(page, wrapper);
    }

    private Map<String, FieldDefinition> loadCustomFields(Long projectId, String typeCode) {
        List<FieldDefinition> definitions = fieldDefinitionService.listByProjectAndType(projectId, typeCode);
        Map<String, FieldDefinition> map = new LinkedHashMap<>();
        for (FieldDefinition def : definitions) {
            if (def.getSystemFlag() == null || def.getSystemFlag() == 0) {
                map.put(def.getFieldKey(), def);
            }
        }
        return map;
    }

    private void applySort(QueryWrapper<PmWorkItem> wrapper, List<SortSpec> sortList) {
        if (sortList == null || sortList.isEmpty()) {
            wrapper.orderByDesc("create_time");
            return;
        }
        for (SortSpec sort : sortList) {
            boolean asc = "ASC".equalsIgnoreCase(sort.getOrder());
            if (sort.getField() != null && sort.getField().startsWith("custom.")) {
                String key = sort.getField().substring("custom.".length());
                wrapper.orderBy(true, asc, JsonSqlDialect.orderBy("custom_fields", key));
            } else {
                wrapper.orderBy(true, asc, sort.getField());
            }
        }
    }
}
