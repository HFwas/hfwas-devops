package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.extract.VariableExtractor;
import com.hfwas.devops.apitest.debugger.extract.VariableExtractor.ExtractRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VariableExtractor — 变量提取器单元测试
 * <p>
 * 覆盖：
 * - 3 种提取来源（RESPONSE_STATUS / RESPONSE_HEADERS / RESPONSE_BODY）
 * - 边界场景（null 入参、空列表、无效值）
 * - 多规则、重复规则
 *
 * @author hfwas
 */
@DisplayName("VariableExtractor — 变量提取器")
@ExtendWith(MockitoExtension.class)
class VariableExtractorTest {

    @InjectMocks
    private VariableExtractor variableExtractor;

    @Nested
    @DisplayName("extract — 空/边界输入")
    class ExtractEmptyInput {

        @Test
        @DisplayName("空提取规则列表应返回空 Map")
        void emptyList() {
            Map<String, String> result = variableExtractor.extract(
                    Collections.emptyList(), 200, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 提取规则列表应返回空 Map")
        void nullList() {
            Map<String, String> result = variableExtractor.extract(
                    null, 200, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null source 应跳过该规则")
        void nullSourceShouldSkip() {
            ExtractRule rule = new ExtractRule("var1", "$.data.id", null);
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null variableName 应跳过该规则")
        void nullVariableNameShouldSkip() {
            ExtractRule rule = new ExtractRule(null, "$.data.id", "RESPONSE_BODY");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extract — 来源：RESPONSE_STATUS")
    class ExtractSourceResponseStatus {

        @Test
        @DisplayName("从响应状态码提取成功")
        void extractFromStatusCode() {
            ExtractRule rule = new ExtractRule("statusCode", null, "RESPONSE_STATUS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, null);
            assertThat(result).containsEntry("statusCode", "200");
        }

        @Test
        @DisplayName("statusCode 为 null 时跳过")
        void nullStatusCode() {
            ExtractRule rule = new ExtractRule("statusCode", null, "RESPONSE_STATUS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), null, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("404 状态码提取")
        void extract404StatusCode() {
            ExtractRule rule = new ExtractRule("statusCode", null, "RESPONSE_STATUS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 404, null, null);
            assertThat(result).containsEntry("statusCode", "404");
        }
    }

    @Nested
    @DisplayName("extract — 来源：RESPONSE_HEADERS")
    class ExtractSourceResponseHeaders {

        @Test
        @DisplayName("从响应头提取成功")
        void extractFromHeader() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            ExtractRule rule = new ExtractRule("contentType", "Content-Type", "RESPONSE_HEADERS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, headers, null);
            assertThat(result).containsEntry("contentType", "application/json");
        }

        @Test
        @DisplayName("header 不存在时跳过")
        void headerNotFound() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            ExtractRule rule = new ExtractRule("custom", "X-Custom", "RESPONSE_HEADERS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, headers, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("expression 为 null 时跳过")
        void nullExpression() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            ExtractRule rule = new ExtractRule("contentType", null, "RESPONSE_HEADERS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, headers, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("responseHeaders 为 null 时跳过")
        void nullHeaders() {
            ExtractRule rule = new ExtractRule("contentType", "Content-Type", "RESPONSE_HEADERS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("多个 header 中提取正确的值")
        void multipleHeaders() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Request-Id", "abc-123");
            headers.put("Set-Cookie", "session=xyz");
            ExtractRule rule = new ExtractRule("requestId", "X-Request-Id", "RESPONSE_HEADERS");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, headers, null);
            assertThat(result).containsEntry("requestId", "abc-123");
        }
    }

    @Nested
    @DisplayName("extract — 来源：RESPONSE_BODY")
    class ExtractSourceResponseBody {

        @Test
        @DisplayName("从响应体提取成功（返回完整 body）")
        void extractFromBody() {
            ExtractRule rule = new ExtractRule("body", "$.data.id", "RESPONSE_BODY");
            String responseBody = "{\"data\":{\"id\":1,\"name\":\"test\"}}";
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, responseBody);
            assertThat(result).containsEntry("body", responseBody);
        }

        @Test
        @DisplayName("body 为 null 时跳过")
        void nullBody() {
            ExtractRule rule = new ExtractRule("body", "$.data.id", "RESPONSE_BODY");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("body 为空字符串时返回空字符串")
        void emptyBody() {
            ExtractRule rule = new ExtractRule("body", "$.data.id", "RESPONSE_BODY");
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, "");
            assertThat(result).containsEntry("body", "");
        }

        @Test
        @DisplayName("expression 为 null 时返回完整 body")
        void nullExpressionBody() {
            ExtractRule rule = new ExtractRule("body", null, "RESPONSE_BODY");
            String responseBody = "{\"data\":{\"id\":1}}";
            Map<String, String> result = variableExtractor.extract(
                    Collections.singletonList(rule), 200, null, responseBody);
            assertThat(result).containsEntry("body", responseBody);
        }
    }

    @Nested
    @DisplayName("extract — 多规则与重复规则")
    class ExtractMultipleRules {

        @Test
        @DisplayName("多个提取规则同时执行")
        void multipleRules() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            List<ExtractRule> rules = Arrays.asList(
                    new ExtractRule("statusCode", null, "RESPONSE_STATUS"),
                    new ExtractRule("contentType", "Content-Type", "RESPONSE_HEADERS"),
                    new ExtractRule("body", "$.data.id", "RESPONSE_BODY")
            );

            Map<String, String> result = variableExtractor.extract(
                    rules, 200, headers, "{\"data\":{\"id\":1}}");

            assertThat(result)
                    .containsEntry("statusCode", "200")
                    .containsEntry("contentType", "application/json")
                    .containsKey("body");
        }

        @Test
        @DisplayName("重复的 variableName 后面覆盖前面")
        void duplicateVariableName() {
            List<ExtractRule> rules = Arrays.asList(
                    new ExtractRule("var", "$.data.id", "RESPONSE_BODY"),
                    new ExtractRule("var", "Content-Type", "RESPONSE_HEADERS")
            );

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            Map<String, String> result = variableExtractor.extract(
                    rules, 200, headers, "{\"data\":{\"id\":1}}");

            // 后面的覆盖前面的
            assertThat(result).containsEntry("var", "application/json");
        }

        @Test
        @DisplayName("混合成功和跳过的提取规则")
        void mixedSuccessAndSkip() {
            List<ExtractRule> rules = Arrays.asList(
                    new ExtractRule("status", null, "RESPONSE_STATUS"),
                    new ExtractRule("missing", "X-Missing", "RESPONSE_HEADERS"),
                    new ExtractRule("body", null, "RESPONSE_BODY")
            );

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            Map<String, String> result = variableExtractor.extract(
                    rules, 200, headers, "{\"id\":1}");

            assertThat(result)
                    .containsEntry("status", "200")
                    .containsEntry("body", "{\"id\":1}")
                    .doesNotContainKey("missing");
        }
    }
}