package com.hfwas.devops.pm.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.service.FieldDefinitionService;
import com.hfwas.devops.pm.query.model.*;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QueryEngine {

    private final PmWorkItemMapper workItemMapper;
    private final FieldDefinitionService fieldDefinitionService;
    private final FieldResolver fieldResolver;

    private static final Set<String> SYSTEM_SORT_FIELDS = Set.of(
            "id", "item_no", "title", "status", "priority", "assignee_id", "reporter_id",
            "project_id", "type_code", "create_time", "update_time", "module_id",
            "sprint_id", "parent_id"
    );

    public IPage<PmWorkItem> execute(QuerySpec spec, Long tenantId) {
        Map<String, FieldDefinition> customFieldMap = loadCustomFields(spec.getProjectId(), spec.getTypeCode());
        QueryWrapper<PmWorkItem> wrapper = new QueryWrapper<>();
        if (spec.getProjectId() != null) {
            // Caller has already asserted access to this project (deep-link friendly).
            wrapper.eq("project_id", spec.getProjectId());
        } else {
            if (tenantId == null) {
                throw new BizException(ResultCode.TENANT_CONTEXT_MISSING);
            }
            wrapper.apply(
                    "project_id IN (SELECT id FROM pm_project WHERE tenant_id = {0} AND del_flag = 0)",
                    tenantId);
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
        applySort(wrapper, spec.getSort(), customFieldMap);
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

    private void applySort(QueryWrapper<PmWorkItem> wrapper, List<SortSpec> sortList,
                           Map<String, FieldDefinition> customFieldMap) {
        if (sortList == null || sortList.isEmpty()) {
            wrapper.orderByDesc("create_time");
            return;
        }
        for (SortSpec sort : sortList) {
            String field = sort.getField();
            if (StringUtils.isBlank(field)) {
                continue;
            }
            boolean asc = "ASC".equalsIgnoreCase(sort.getOrder());
            if (field.startsWith("custom.")) {
                String key = field.substring("custom.".length());
                if (!customFieldMap.containsKey(key)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "不支持的自定义排序字段: " + key);
                }
                wrapper.orderBy(true, asc, JsonSqlDialect.orderBy("custom_fields", key));
            } else if (SYSTEM_SORT_FIELDS.contains(field)) {
                wrapper.orderBy(true, asc, field);
            } else {
                throw new BizException(ResultCode.BAD_REQUEST, "不支持的排序字段: " + field);
            }
        }
    }
}
