package com.hfwas.devops.pm.query.engine;

/**
 * SQLite JSON 字段 SQL 片段（custom_fields 存 TEXT JSON）
 */
public final class JsonSqlDialect {

    private JsonSqlDialect() {
    }

    public static String jsonExtractText(String column, String fieldKey) {
        return "json_extract(" + column + ", '$." + fieldKey + "')";
    }

    public static String jsonExtractUnquoted(String column, String fieldKey) {
        return "json_extract(" + column + ", '$." + fieldKey + "')";
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
}
