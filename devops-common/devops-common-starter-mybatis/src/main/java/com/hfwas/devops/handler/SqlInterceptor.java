package com.hfwas.devops.handler;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.handler
 * @date 2025/1/16
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = Statement.class),
        @Signature(type = StatementHandler.class, method = "batch", args = Statement.class)
})
public class SqlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取 StatementHandler
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        Object parameterObject1 = statementHandler.getParameterHandler().getParameterObject();

        // 获取原始 SQL
        String originalSql = statementHandler.getBoundSql().getSql().trim();
        originalSql = originalSql.replaceAll("[\\s]+", " ");

        // 获取参数对象
        Object parameterObject = boundSql.getParameterObject();
        Map<String, Object> parameterMap = getParameterMap(parameterObject);

        // 替换参数，生成真实执行的 SQL
        String executableSql = replacePlaceholders(originalSql, parameterMap);
        System.out.println("Executable SQL: " + executableSql);

        // 执行原方法
        return invocation.proceed();
    }

    private Map<String, Object> getParameterMap(Object parameterObject) {
        Map<String, Object> parameterMap = new HashMap<>();
        if (parameterObject instanceof Map) {
            parameterMap.putAll((Map<String, Object>) parameterObject);
        } else if (parameterObject != null) {
            // 尝试将对象的字段作为参数
            for (Field field : parameterObject.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    parameterMap.put(field.getName(), field.get(parameterObject));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return parameterMap;
    }

    private String replacePlaceholders(String sql, Map<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String value = entry.getValue() == null ? "NULL" : getFormattedValue(entry.getValue());
            sql = sql.replaceFirst("\\?", value);
        }
        return sql;
    }

    private String getFormattedValue(Object value) {
        // Handle primitive types and strings
        if (value instanceof String) {
            return "'" + value + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof java.util.Date) {
            // Format Date to SQL format
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return "'" + sdf.format((java.util.Date) value) + "'";
        } else if (value instanceof java.util.Collection) {
            // Handle collections (List, Set, etc.)
            return handleCollection((java.util.Collection<?>) value);
        } else {
            // Handle custom complex objects
            return getObjectStringRepresentation(value);
        }
    }

    private String handleCollection(java.util.Collection<?> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        boolean first = true;
        for (Object item : collection) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(getFormattedValue(item));
        }
        sb.append(")");
        return sb.toString();
    }

    private String getObjectStringRepresentation(Object obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        // Use reflection to access fields of the object
        Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true);
            try {
                Object fieldValue = fields[i].get(obj);
                if (fieldValue == null) {
                    sb.append("NULL");
                } else if (fieldValue instanceof String) {
                    sb.append("'").append(fieldValue).append("'");
                } else if (fieldValue instanceof Number || fieldValue instanceof Boolean) {
                    sb.append(fieldValue);
                } else if (fieldValue instanceof java.util.Date) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sb.append("'").append(sdf.format((java.util.Date) fieldValue)).append("'");
                } else {
                    // Recursively process nested objects if necessary
                    sb.append(getObjectStringRepresentation(fieldValue));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                sb.append("NULL");
            }
            if (i < fields.length - 1) sb.append(", ");
        }

        sb.append(")");
        return sb.toString();
    }


}
