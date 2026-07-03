package com.hfwas.devops.pm.query.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hfwas.devops.pm.field.engine.FieldTypeRegistry;
import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.query.model.*;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FieldResolver {

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "title", "status", "type_code", "priority", "assignee_id", "reporter_id",
            "parent_id", "project_id", "create_time", "update_time", "sprint_id"
    );

    private static final Map<String, String> COLUMN_MAP = Map.ofEntries(
            Map.entry("type_code", "type_code"),
            Map.entry("assignee_id", "assignee_id"),
            Map.entry("reporter_id", "reporter_id"),
            Map.entry("parent_id", "parent_id"),
            Map.entry("project_id", "project_id"),
            Map.entry("sprint_id", "sprint_id"),
            Map.entry("create_time", "create_time"),
            Map.entry("update_time", "update_time")
    );

    private final FieldTypeRegistry fieldTypeRegistry;

    public void applyCondition(QueryWrapper<PmWorkItem> wrapper,
                               QueryCondition condition,
                               Map<String, FieldDefinition> customFieldMap) {
        String field = condition.getField();
        if (StringUtils.isBlank(field)) {
            return;
        }
        if (field.startsWith("custom.")) {
            String key = field.substring("custom.".length());
            FieldDefinition def = customFieldMap.get(key);
            if (def == null) {
                throw new IllegalArgumentException("Unknown custom field: " + key);
            }
            String sql = fieldTypeRegistry.get(def.getFieldType()).buildQuerySql(def, condition);
            wrapper.apply(sql);
            return;
        }
        if (!SYSTEM_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unknown system field: " + field);
        }
        applySystemCondition(wrapper, field, condition);
    }

    private void applySystemCondition(QueryWrapper<PmWorkItem> wrapper, String field, QueryCondition condition) {
        String column = COLUMN_MAP.getOrDefault(field, field);
        QueryOperator op = condition.getOperator();
        Object value = condition.getValue();
        switch (op) {
            case EQ -> wrapper.eq(column, value);
            case NE -> wrapper.ne(column, value);
            case GT -> wrapper.gt(column, value);
            case GTE -> wrapper.ge(column, value);
            case LT -> wrapper.lt(column, value);
            case LTE -> wrapper.le(column, value);
            case LIKE -> wrapper.like(column, value);
            case IN -> wrapper.in(column, toCollection(value));
            case NOT_IN -> wrapper.notIn(column, toCollection(value));
            case IS_NULL -> wrapper.isNull(column);
            case IS_NOT_NULL -> wrapper.isNotNull(column);
            case BETWEEN -> {
                if (value instanceof Collection<?> c) {
                    List<?> list = new ArrayList<>(c);
                    if (list.size() >= 2) {
                        wrapper.between(column, list.get(0), list.get(1));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array);
        }
        return List.of(value);
    }

    public void applyGroup(QueryWrapper<PmWorkItem> wrapper,
                           QueryConditionGroup group,
                           Map<String, FieldDefinition> customFieldMap) {
        if (group == null) {
            return;
        }
        wrapper.and(w -> {
            boolean first = true;
            for (QueryCondition condition : group.getConditions()) {
                if (first) {
                    applyCondition(w, condition, customFieldMap);
                    first = false;
                } else if (group.getLogic() == QueryLogic.OR) {
                    w.or(sub -> applyCondition(sub, condition, customFieldMap));
                } else {
                    applyCondition(w, condition, customFieldMap);
                }
            }
            for (QueryConditionGroup nested : group.getGroups()) {
                if (group.getLogic() == QueryLogic.OR) {
                    w.or(sub -> applyGroup(sub, nested, customFieldMap));
                } else {
                    applyGroup(w, nested, customFieldMap);
                }
            }
        });
    }
}
