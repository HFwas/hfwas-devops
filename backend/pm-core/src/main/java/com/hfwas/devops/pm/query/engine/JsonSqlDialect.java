package com.hfwas.devops.pm.query.engine;

import com.hfwas.devops.pm.query.model.QueryOperator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MySQL JSON 字段 SQL 片段（custom_fields 存 TEXT JSON）。
 * 字段 key 必须经过白名单格式校验，禁止拼入任意标识符。
 */
public final class JsonSqlDialect {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}:\\d{2})?$");

    private JsonSqlDialect() {
    }

    public static void validateFieldKey(String fieldKey) {
        if (fieldKey == null || !FIELD_KEY_PATTERN.matcher(fieldKey).matches()) {
            throw new IllegalArgumentException("非法自定义字段编码: " + fieldKey);
        }
    }

    public static String jsonExtractText(String column, String fieldKey) {
        validateFieldKey(fieldKey);
        return "CASE WHEN JSON_VALID(" + column + ") THEN JSON_EXTRACT(" + column + ", '$." + fieldKey + "') ELSE NULL END";
    }

    public static String jsonExtractUnquoted(String column, String fieldKey) {
        validateFieldKey(fieldKey);
        return "CASE WHEN JSON_VALID(" + column + ") THEN JSON_UNQUOTE(JSON_EXTRACT("
                + column + ", '$." + fieldKey + "')) ELSE NULL END";
    }

    public static String eq(String column, String fieldKey, String escapedValue) {
        return jsonExtractUnquoted(column, fieldKey) + " = '" + escapedValue + "'";
    }

    public static String ne(String column, String fieldKey, String escapedValue) {
        return jsonExtractUnquoted(column, fieldKey) + " <> '" + escapedValue + "'";
    }

    public static String like(String column, String fieldKey, String escapedValue) {
        return jsonExtractUnquoted(column, fieldKey) + " LIKE '%" + escapedValue + "%'";
    }

    public static String notLike(String column, String fieldKey, String escapedValue) {
        return jsonExtractUnquoted(column, fieldKey) + " NOT LIKE '%" + escapedValue + "%'";
    }

    public static String isNull(String column, String fieldKey) {
        return jsonExtractUnquoted(column, fieldKey) + " IS NULL";
    }

    public static String isNotNull(String column, String fieldKey) {
        return jsonExtractUnquoted(column, fieldKey) + " IS NOT NULL";
    }

    public static String in(String column, String fieldKey, String inList) {
        return jsonExtractUnquoted(column, fieldKey) + " IN (" + inList + ")";
    }

    public static String notIn(String column, String fieldKey, String inList) {
        return jsonExtractUnquoted(column, fieldKey) + " NOT IN (" + inList + ")";
    }

    public static String orderBy(String column, String fieldKey) {
        return jsonExtractUnquoted(column, fieldKey);
    }

    /**
     * 自定义字段大小比较。数值用 DECIMAL，日期用 DATE/DATETIME，值做严格格式校验后内联。
     */
    public static String compare(String column, String fieldKey, QueryOperator op, Object value, String castType) {
        String expression = castExpression(column, fieldKey, castType);
        return switch (op) {
            case GT -> expression + " > " + literal(value, castType);
            case GTE -> expression + " >= " + literal(value, castType);
            case LT -> expression + " < " + literal(value, castType);
            case LTE -> expression + " <= " + literal(value, castType);
            case BETWEEN -> {
                List<Object> values = toList(value);
                if (values.size() < 2) {
                    throw new IllegalArgumentException("BETWEEN 需要两个值");
                }
                yield expression + " BETWEEN " + literal(values.get(0), castType)
                        + " AND " + literal(values.get(1), castType);
            }
            default -> throw new IllegalArgumentException("不支持的自定义字段比较操作符: " + op);
        };
    }

    private static String castExpression(String column, String fieldKey, String castType) {
        if (!"DECIMAL(30,10)".equals(castType) && !"DATE".equals(castType) && !"DATETIME".equals(castType)) {
            throw new IllegalArgumentException("不支持的比较类型: " + castType);
        }
        return "CAST(" + jsonExtractUnquoted(column, fieldKey) + " AS " + castType + ")";
    }

    private static String literal(Object value, String castType) {
        String text = String.valueOf(value).trim();
        if ("DECIMAL(30,10)".equals(castType)) {
            if (!NUMBER_PATTERN.matcher(text).matches()) {
                throw new IllegalArgumentException("数值格式错误: " + text);
            }
            return text;
        }
        if (!DATE_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("日期格式错误: " + text);
        }
        return "'" + text + "'";
    }

    private static List<Object> toList(Object value) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of(value);
    }
}
