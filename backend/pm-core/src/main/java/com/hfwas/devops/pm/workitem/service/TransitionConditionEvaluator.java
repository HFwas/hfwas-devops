package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryConditionGroup;
import com.hfwas.devops.pm.query.model.QueryLogic;
import com.hfwas.devops.pm.query.model.QueryOperator;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.TransitionConditionSpec;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 对单条事项内存评估 Transition Condition（QuerySpec 同形）。
 */
@Service
@RequiredArgsConstructor
public class TransitionConditionEvaluator {

    public static final String CURRENT_USER = "__current_user__";

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "title", "description", "status", "type_code", "priority", "assignee_id", "reporter_id",
            "parent_id", "project_id", "item_no", "create_time", "update_time", "sprint_id", "module_id"
    );

    private final CurrentUserAccessor currentUserAccessor;

    public boolean matches(PmWorkItem item, TransitionConditionSpec spec) {
        if (spec == null || spec.isEmpty()) {
            return true;
        }
        if (item == null) {
            return false;
        }
        QueryLogic logic = spec.getLogic() != null ? spec.getLogic() : QueryLogic.AND;
        List<Boolean> parts = new ArrayList<>();
        if (spec.getConditions() != null) {
            for (QueryCondition condition : spec.getConditions()) {
                parts.add(matchCondition(item, condition));
            }
        }
        if (spec.getGroups() != null) {
            for (QueryConditionGroup group : spec.getGroups()) {
                parts.add(matchGroup(item, group));
            }
        }
        if (parts.isEmpty()) {
            return true;
        }
        if (logic == QueryLogic.OR) {
            return parts.stream().anyMatch(Boolean::booleanValue);
        }
        return parts.stream().allMatch(Boolean::booleanValue);
    }

    public void assertMatches(PmWorkItem item, TransitionConditionSpec spec) {
        if (!matches(item, spec)) {
            throw new IllegalArgumentException("当前事项不满足该流转的可见条件");
        }
    }

    private boolean matchGroup(PmWorkItem item, QueryConditionGroup group) {
        if (group == null) {
            return true;
        }
        QueryLogic logic = group.getLogic() != null ? group.getLogic() : QueryLogic.AND;
        List<Boolean> parts = new ArrayList<>();
        if (group.getConditions() != null) {
            for (QueryCondition condition : group.getConditions()) {
                parts.add(matchCondition(item, condition));
            }
        }
        if (group.getGroups() != null) {
            for (QueryConditionGroup nested : group.getGroups()) {
                parts.add(matchGroup(item, nested));
            }
        }
        if (parts.isEmpty()) {
            return true;
        }
        if (logic == QueryLogic.OR) {
            return parts.stream().anyMatch(Boolean::booleanValue);
        }
        return parts.stream().allMatch(Boolean::booleanValue);
    }

    private boolean matchCondition(PmWorkItem item, QueryCondition condition) {
        if (condition == null || StringUtils.isBlank(condition.getField())) {
            return true;
        }
        QueryOperator op = condition.getOperator();
        if (op == null) {
            throw new IllegalArgumentException("条件缺少 operator: " + condition.getField());
        }
        Object actual = resolveFieldValue(item, condition.getField().trim());
        Object expected = resolveExpected(condition.getValue());
        return compare(actual, op, expected);
    }

    private Object resolveExpected(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s && CURRENT_USER.equals(s.trim())) {
            return currentUserAccessor.currentUserId();
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : collection) {
                list.add(resolveExpected(item));
            }
            return list;
        }
        if (value instanceof Object[] array) {
            List<Object> list = new ArrayList<>();
            for (Object item : array) {
                list.add(resolveExpected(item));
            }
            return list;
        }
        return value;
    }

    private Object resolveFieldValue(PmWorkItem item, String field) {
        if (field.startsWith("custom.")) {
            String key = field.substring("custom.".length());
            Map<String, Object> custom = item.getCustomFields();
            return custom != null ? custom.get(key) : null;
        }
        if (!SYSTEM_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unknown system field: " + field);
        }
        return switch (field) {
            case "title" -> item.getTitle();
            case "description" -> item.getDescription();
            case "status" -> item.getStatus();
            case "type_code" -> item.getTypeCode();
            case "priority" -> item.getPriority();
            case "assignee_id" -> item.getAssigneeId();
            case "reporter_id" -> item.getReporterId();
            case "parent_id" -> item.getParentId();
            case "project_id" -> item.getProjectId();
            case "item_no" -> item.getItemNo();
            case "create_time" -> item.getCreateTime();
            case "update_time" -> item.getUpdateTime();
            case "sprint_id" -> item.getSprintId();
            case "module_id" -> item.getModuleId();
            default -> null;
        };
    }

    private boolean compare(Object actual, QueryOperator op, Object expected) {
        return switch (op) {
            case EQ -> equalsLoose(actual, expected);
            case NE -> !equalsLoose(actual, expected);
            case GT -> compareNumbers(actual, expected) > 0;
            case GTE -> compareNumbers(actual, expected) >= 0;
            case LT -> compareNumbers(actual, expected) < 0;
            case LTE -> compareNumbers(actual, expected) <= 0;
            case LIKE -> actual != null && expected != null
                    && String.valueOf(actual).toLowerCase().contains(String.valueOf(expected).toLowerCase());
            case IN -> collectionOf(expected).stream().anyMatch(v -> equalsLoose(actual, v));
            case NOT_IN -> collectionOf(expected).stream().noneMatch(v -> equalsLoose(actual, v));
            case IS_NULL -> isBlank(actual);
            case IS_NOT_NULL -> !isBlank(actual);
            case BETWEEN -> {
                List<?> range = new ArrayList<>(collectionOf(expected));
                if (range.size() < 2) {
                    yield false;
                }
                yield compareNumbers(actual, range.get(0)) >= 0 && compareNumbers(actual, range.get(1)) <= 0;
            }
        };
    }

    private boolean equalsLoose(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number || b instanceof Number) {
            try {
                return Double.compare(toDouble(a), toDouble(b)) == 0;
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Objects.equals(String.valueOf(a), String.valueOf(b));
    }

    private int compareNumbers(Object a, Object b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("数值比较不能为空");
        }
        return Double.compare(toDouble(a), toDouble(b));
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return StringUtils.isBlank(s);
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    private Collection<?> collectionOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> c) {
            return c;
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array);
        }
        return List.of(value);
    }
}
