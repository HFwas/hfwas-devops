package com.hfwas.devops.apitest.debugger.extract;

import com.hfwas.devops.apitest.common.enums.ExtractSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变量提取器
 * <p>
 * 从响应中提取变量值，用于后续接口引用。
 * 支持从响应体、响应头、响应状态码中提取。
 *
 * @author hfwas
 */
@Slf4j
@Component
public class VariableExtractor {

    /**
     * 提取变量
     *
     * @param extracts        变量提取规则
     * @param statusCode      响应状态码
     * @param responseHeaders 响应头
     * @param responseBody    响应体
     * @return 提取的变量映射
     */
    public Map<String, String> extract(List<ExtractRule> extracts,
                                        Integer statusCode,
                                        Map<String, String> responseHeaders,
                                        String responseBody) {
        Map<String, String> result = new LinkedHashMap<>();

        if (extracts == null || extracts.isEmpty()) {
            return result;
        }

        for (ExtractRule rule : extracts) {
            String value = extractSingle(rule, statusCode, responseHeaders, responseBody);
            if (value != null && rule.getVariableName() != null) {
                result.put(rule.getVariableName(), value);
            }
        }

        return result;
    }

    /**
     * 提取单个变量
     */
    private String extractSingle(ExtractRule rule,
                                  Integer statusCode,
                                  Map<String, String> responseHeaders,
                                  String responseBody) {
        if (rule.getSource() == null) {
            return null;
        }

        return switch (rule.getSource()) {
            case "RESPONSE_STATUS" -> statusCode != null ? String.valueOf(statusCode) : null;
            case "RESPONSE_HEADERS" -> {
                if (rule.getExpression() != null && responseHeaders != null) {
                    yield responseHeaders.get(rule.getExpression());
                }
                yield null;
            }
            case "RESPONSE_BODY" -> {
                if (rule.getExpression() != null && responseBody != null) {
                    // TODO: 集成 JSONPath 提取
                    // 当前占位实现
                    yield responseBody;
                }
                yield responseBody;
            }
            default -> null;
        };
    }

    /**
     * 提取规则
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ExtractRule {
        private String variableName;
        private String expression;
        private String source;
    }
}