package com.hfwas.devops.pm.field.spi.handler;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.field.spi.FieldTypeHandler;
import com.hfwas.devops.pm.query.engine.JsonSqlDialect;
import com.hfwas.devops.pm.query.model.QueryCondition;
import com.hfwas.devops.pm.query.model.QueryOperator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

@Component
public abstract class AbstractFieldTypeHandler implements FieldTypeHandler {

    protected void requireNonNull(Object value, FieldDefinition definition) {
        if (value == null || (value instanceof String s && StringUtils.isBlank(s))) {
            throw new IllegalArgumentException(definition.getFieldName() + " 不能为空");
        }
    }

    protected String jsonPath(String fieldKey) {
        return "$." + fieldKey;
    }

    protected String buildCustomFieldSql(String fieldKey, QueryCondition condition) {
        QueryOperator op = condition.getOperator();
        Object value = condition.getValue();
        return switch (op) {
            case EQ -> JsonSqlDialect.eq("custom_fields", fieldKey, escape(value));
            case NE -> JsonSqlDialect.ne("custom_fields", fieldKey, escape(value));
            case LIKE -> JsonSqlDialect.like("custom_fields", fieldKey, escape(value));
            case IS_NULL -> JsonSqlDialect.isNull("custom_fields", fieldKey);
            case IS_NOT_NULL -> JsonSqlDialect.isNotNull("custom_fields", fieldKey);
            case IN -> JsonSqlDialect.in("custom_fields", fieldKey, inList(value));
            case NOT_IN -> JsonSqlDialect.notIn("custom_fields", fieldKey, inList(value));
            case GT, GTE, LT, LTE, BETWEEN -> {
                String castType = comparisonCastType();
                if (castType == null) {
                    throw new IllegalArgumentException("Unsupported operator for custom field: " + op);
                }
                yield JsonSqlDialect.compare("custom_fields", fieldKey, op, value, castType);
            }
            default -> throw new IllegalArgumentException("Unsupported operator for custom field: " + op);
        };
    }

    protected String escape(Object value) {
        return String.valueOf(value).replace("'", "''");
    }

    /** 支持大小比较的字段返回 SQL CAST 类型，其余返回 null。 */
    protected String comparisonCastType() {
        return null;
    }

    protected String inList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(v -> "'" + escape(v) + "'").collect(Collectors.joining(","));
        }
        return "'" + escape(value) + "'";
    }

    @Override
    public String buildQuerySql(FieldDefinition definition, QueryCondition condition) {
        return buildCustomFieldSql(definition.getFieldKey(), condition);
    }
}
