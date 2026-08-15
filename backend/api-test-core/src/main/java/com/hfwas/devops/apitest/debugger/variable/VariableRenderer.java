package com.hfwas.devops.apitest.debugger.variable;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量渲染引擎
 * <p>
 * 将字符串中的 {{varName}} 占位符替换为实际变量值。
 * 支持多层变量优先级：临时变量 > 环境变量 > 全局变量
 * 支持默认值语法：{{varName:defaultValue}}
 *
 * @author hfwas
 */
@Slf4j
@Component
public class VariableRenderer {

    /** 变量占位符正则：{{ variableName }} 或 {{ variableName:defaultValue }} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*(?::\\s*([^}]*))?\\s*}}");

    /** 临时变量（当前调试会话） */
    private final Map<String, String> temporaryVariables = new ConcurrentHashMap<>();

    /**
     * 渲染字符串中的变量占位符
     *
     * @param template  包含占位符的模板字符串
     * @param variables 变量映射（环境变量等）
     * @return 渲染后的字符串
     */
    public String render(String template, Map<String, String> variables) {
        if (StrUtil.isBlank(template)) {
            return template;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        int lastEnd = 0;

        while (matcher.find()) {
            // 添加占位符前的文本
            result.append(template, lastEnd, matcher.start());

            String varName = matcher.group(1).trim();
            String defaultValue = matcher.group(2);

            // 按优先级查找变量值
            String value = resolveVariable(varName, variables);

            if (value != null) {
                result.append(value);
            } else if (defaultValue != null) {
                // 使用默认值
                result.append(defaultValue);
            } else {
                // 未找到变量，保留原占位符
                result.append(matcher.group());
                log.warn("变量未找到: {}, 保留占位符", varName);
            }

            lastEnd = matcher.end();
        }

        // 添加剩余文本
        result.append(template.substring(lastEnd));

        return result.toString();
    }

    /**
     * 渲染 Map 中的所有值
     */
    public Map<String, String> renderMap(Map<String, String> map, Map<String, String> variables) {
        if (map == null) {
            return null;
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(key, render(value, variables)));
        return result;
    }

    /**
     * 设置临时变量
     */
    public void setTemporaryVariable(String name, String value) {
        if (name != null) {
            temporaryVariables.put(name, value);
        }
    }

    /**
     * 批量设置临时变量
     */
    public void setTemporaryVariables(Map<String, String> variables) {
        if (variables != null) {
            temporaryVariables.putAll(variables);
        }
    }

    /**
     * 清空临时变量
     */
    public void clearTemporaryVariables() {
        temporaryVariables.clear();
    }

    /**
     * 按优先级解析变量值
     * 优先级：临时变量 > 传入变量（环境变量）
     */
    private String resolveVariable(String varName, Map<String, String> variables) {
        // 1. 临时变量（最高优先级）
        if (temporaryVariables.containsKey(varName)) {
            return temporaryVariables.get(varName);
        }

        // 2. 传入变量（环境变量）
        if (variables != null && variables.containsKey(varName)) {
            return variables.get(varName);
        }

        return null;
    }
}