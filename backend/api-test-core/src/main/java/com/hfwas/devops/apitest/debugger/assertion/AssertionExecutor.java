package com.hfwas.devops.apitest.debugger.assertion;

import cn.hutool.core.util.StrUtil;
import com.hfwas.devops.apitest.common.enums.AssertionSourceEnum;
import com.hfwas.devops.apitest.common.enums.CompareTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 断言执行器
 * <p>
 * 根据断言规则逐条执行比较，返回断言结果列表。
 *
 * @author hfwas
 */
@Slf4j
@Component
public class AssertionExecutor {

    /**
     * 执行断言
     *
     * @param assertions      断言规则列表
     * @param statusCode      响应状态码
     * @param responseHeaders 响应头
     * @param responseBody    响应体
     * @param durationMs      响应耗时
     * @return 断言结果列表
     */
    public List<Map<String, Object>> execute(List<AssertionRule> assertions,
                                              Integer statusCode,
                                              Map<String, String> responseHeaders,
                                              String responseBody,
                                              Long durationMs) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (assertions == null || assertions.isEmpty()) {
            return results;
        }

        for (AssertionRule rule : assertions) {
            Map<String, Object> result = executeSingle(rule, statusCode, responseHeaders, responseBody, durationMs);
            results.add(result);
        }

        return results;
    }

    /**
     * 执行单条断言
     */
    private Map<String, Object> executeSingle(AssertionRule rule,
                                               Integer statusCode,
                                               Map<String, String> responseHeaders,
                                               String responseBody,
                                               Long durationMs) {
        // 获取实际值
        String actualValue = getActualValue(rule.getSource(), rule.getExpression(),
                statusCode, responseHeaders, responseBody, durationMs);

        // 比较
        boolean passed = compare(rule.getCompareType(), actualValue, rule.getExpectedValue());

        // 构建结果
        return new java.util.LinkedHashMap<>() {{
            put("name", rule.getName() != null ? rule.getName() : rule.getSource() + ":" + rule.getExpression());
            put("source", rule.getSource());
            put("compareType", rule.getCompareType());
            put("expression", rule.getExpression());
            put("expected", rule.getExpectedValue());
            put("actual", actualValue);
            put("passed", passed);
        }};
    }

    /**
     * 获取实际值
     */
    private String getActualValue(String source, String expression,
                                   Integer statusCode,
                                   Map<String, String> responseHeaders,
                                   String responseBody,
                                   Long durationMs) {
        if (source == null) {
            return null;
        }

        return switch (source) {
            case "RESPONSE_STATUS" -> statusCode != null ? String.valueOf(statusCode) : null;
            case "RESPONSE_HEADERS" -> {
                if (expression != null && responseHeaders != null) {
                    yield responseHeaders.get(expression);
                }
                yield null;
            }
            case "RESPONSE_BODY" -> {
                if (expression != null && responseBody != null) {
                    // TODO: 支持 JSONPath 提取
                    // 当前直接返回整个响应体
                    yield responseBody;
                }
                yield responseBody;
            }
            case "RESPONSE_TIME" -> durationMs != null ? String.valueOf(durationMs) : null;
            default -> null;
        };
    }

    /**
     * 比较实际值和期望值
     */
    private boolean compare(String compareType, String actualValue, String expectedValue) {
        if (actualValue == null) {
            return false;
        }

        if (compareType == null) {
            compareType = "EQUALS";
        }

        return switch (compareType) {
            case "EQUALS" -> expectedValue != null && actualValue.equals(expectedValue);
            case "NOT_EQUALS" -> expectedValue == null || !actualValue.equals(expectedValue);
            case "CONTAINS" -> expectedValue != null && actualValue.contains(expectedValue);
            case "NOT_CONTAINS" -> expectedValue == null || !actualValue.contains(expectedValue);
            case "REGEX" -> {
                if (expectedValue == null) {
                    yield false;
                }
                try {
                    yield Pattern.compile(expectedValue).matcher(actualValue).find();
                } catch (Exception e) {
                    log.warn("正则表达式错误: {}", expectedValue, e);
                    yield false;
                }
            }
            case "GT" -> compareNumeric(actualValue, expectedValue) > 0;
            case "GTE" -> compareNumeric(actualValue, expectedValue) >= 0;
            case "LT" -> compareNumeric(actualValue, expectedValue) < 0;
            case "LTE" -> compareNumeric(actualValue, expectedValue) <= 0;
            default -> {
                log.warn("不支持的比较方式: {}", compareType);
                yield false;
            }
        };
    }

    /**
     * 数值比较
     */
    private int compareNumeric(String actual, String expected) {
        try {
            double actualNum = Double.parseDouble(actual);
            double expectedNum = Double.parseDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            log.warn("数值比较失败: actual={}, expected={}", actual, expected);
            return 0;
        }
    }

    /**
     * 断言规则
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AssertionRule {
        private String name;
        private String source;
        private String compareType;
        private String expression;
        private String expectedValue;
    }
}