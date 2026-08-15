package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.assertion.AssertionExecutor;
import com.hfwas.devops.apitest.debugger.assertion.AssertionExecutor.AssertionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssertionExecutor — 断言执行器单元测试
 * <p>
 * 覆盖：
 * - 4 种断言来源（RESPONSE_STATUS / RESPONSE_HEADERS / RESPONSE_BODY / RESPONSE_TIME）
 * - 9 种比较方式（EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / REGEX / GT / GTE / LT / LTE）
 * - 边界场景（null 入参、空列表、无效值）
 *
 * @author hfwas
 */
@DisplayName("AssertionExecutor — 断言执行器")
@ExtendWith(MockitoExtension.class)
class AssertionExecutorTest {

    @InjectMocks
    private AssertionExecutor assertionExecutor;

    @Nested
    @DisplayName("execute — 空/边界输入")
    class ExecuteEmptyInput {

        @Test
        @DisplayName("空断言列表应返回空结果")
        void emptyList() {
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.emptyList(), 200, null, null, 0L);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 断言列表应返回空结果")
        void nullList() {
            List<Map<String, Object>> result = assertionExecutor.execute(
                    null, 200, null, null, 0L);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("name 为 null 时应使用 source:expression 作为默认名称")
        void nullNameShouldUseDefault() {
            AssertionRule rule = new AssertionRule(null, "RESPONSE_STATUS", "EQUALS", "status", "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("name")).isEqualTo("RESPONSE_STATUS:status");
        }

        @Test
        @DisplayName("source 为 null 时 actual 为 null，断言不通过")
        void nullSourceShouldReturnFalse() {
            AssertionRule rule = new AssertionRule("test", null, "EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("source 为空字符串时 actual 为 null，断言不通过")
        void emptySourceShouldReturnFalse() {
            AssertionRule rule = new AssertionRule("test", "", "EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("compareType 为 null 时应默认 EQUALS")
        void nullCompareTypeShouldDefaultToEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", null, null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("expectedValue 为 null 时 EQUALS 比较应返回 false")
        void nullExpectedValueEqualsShouldReturnFalse() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "EQUALS", null, null);
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("execute — 来源：RESPONSE_STATUS")
    class ExecuteSourceResponseStatus {

        @Test
        @DisplayName("状态码匹配时 EQUALS 应为 true")
        void statusCodeEquals() {
            AssertionRule rule = new AssertionRule("状态码检查", "RESPONSE_STATUS", "EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("状态码不匹配时 EQUALS 应为 false")
        void statusCodeNotEquals() {
            AssertionRule rule = new AssertionRule("状态码检查", "RESPONSE_STATUS", "EQUALS", null, "404");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("statusCode 为 null 时 EQUALS 应为 false")
        void nullStatusCodeEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), null, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
            assertThat(result.get(0).get("actual")).isNull();
        }

        @Test
        @DisplayName("NOT_EQUALS 比较 — 不等时应为 true")
        void notEqualsShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "NOT_EQUALS", null, "404");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("NOT_EQUALS 比较 — 相等时应为 false")
        void notEqualsShouldFail() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "NOT_EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("RESPONSE_STATUS 的 actual 应为状态码字符串")
        void actualValueShouldBeStatusCode() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "EQUALS", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 304, null, null, 0L);
            assertThat(result.get(0).get("actual")).isEqualTo("304");
        }
    }

    @Nested
    @DisplayName("execute — 来源：RESPONSE_HEADERS")
    class ExecuteSourceResponseHeaders {

        @Test
        @DisplayName("header 存在且值匹配时 EQUALS 应为 true")
        void headerExistsAndMatches() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "EQUALS", "Content-Type", "application/json");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, headers, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("header 存在但值不匹配时 EQUALS 应为 false")
        void headerExistsButNotMatches() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/html");
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "EQUALS", "Content-Type", "application/json");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, headers, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("header 不存在时 EQUALS 应为 false")
        void headerNotExists() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "EQUALS", "X-Custom", "value");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, headers, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
            assertThat(result.get(0).get("actual")).isNull();
        }

        @Test
        @DisplayName("expression 为 null 时实际值为 null，断言不通过")
        void nullExpressionShouldReturnNullActual() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "EQUALS", null, "application/json");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, headers, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
            assertThat(result.get(0).get("actual")).isNull();
        }

        @Test
        @DisplayName("responseHeaders 为 null 时 EQUALS 应为 false")
        void nullHeadersShouldReturnNullActual() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "EQUALS", "Content-Type", "application/json");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("actual")).isNull();
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("多个 header 中 CONTAINS 匹配正确的 header")
        void multipleHeadersContains() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Request-Id", "abc-123");
            headers.put("Set-Cookie", "session=xyz");
            AssertionRule rule = new AssertionRule("test", "RESPONSE_HEADERS", "CONTAINS", "X-Request-Id", "abc-123");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, headers, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("execute — 来源：RESPONSE_BODY")
    class ExecuteSourceResponseBody {

        @Test
        @DisplayName("body 包含期望内容时 CONTAINS 应为 true")
        void bodyContainsExpected() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "CONTAINS", null, "data");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("body 不包含期望内容时 CONTAINS 应为 false")
        void bodyNotContainsExpected() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "CONTAINS", null, "error");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("body 为 null 时 CONTAINS 应为 false")
        void nullBodyContains() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "CONTAINS", null, "data");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("body 为空字符串时 CONTAINS 应为 false")
        void emptyBodyContains() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "CONTAINS", null, "data");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("EXPECT 比较 — body 完全匹配时 EQUALS 应为 true")
        void bodyEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "EQUALS", null, "{\"id\":1}");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"id\":1}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("NOT_CONTAINS 比较 — body 不包含时应为 true")
        void notContainsShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "NOT_CONTAINS", null, "error");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("NOT_CONTAINS 比较 — body 包含时应为 false")
        void notContainsShouldFail() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "NOT_CONTAINS", null, "data");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("execute — 来源：RESPONSE_TIME")
    class ExecuteSourceResponseTime {

        @Test
        @DisplayName("响应时间匹配时 EQUALS 应为 true")
        void responseTimeEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "EQUALS", null, "156");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("响应时间不匹配时 EQUALS 应为 false")
        void responseTimeNotEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "EQUALS", null, "100");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("durationMs 为 null 时 EQUALS 应为 false")
        void nullDurationEquals() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "EQUALS", null, "100");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, null);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
            assertThat(result.get(0).get("actual")).isNull();
        }

        @Test
        @DisplayName("GT 比较 — 实际值大于期望值时应为 true")
        void gtShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "GT", null, "100");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("GT 比较 — 实际值小于期望值时应为 false")
        void gtShouldFail() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "GT", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("GTE 比较 — 实际值等于期望值时应为 true")
        void gteEqualShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "GTE", null, "156");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("LT 比较 — 实际值小于期望值时应为 true")
        void ltShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "LT", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("LTE 比较 — 实际值等于期望值时应为 true")
        void lteEqualShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "LTE", null, "156");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("LTE 比较 — 实际值小于期望值时应为 true")
        void lteLessShouldPass() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "LTE", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("非数值字符串进行数值比较时应返回 false")
        void nonNumericComparisonShouldFail() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_TIME", "GT", null, "abc");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 156L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("execute — 比较方式：REGEX")
    class ExecuteCompareTypeRegex {

        @Test
        @DisplayName("正则匹配成功时应为 true")
        void regexMatches() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "REGEX", null, "\\{\"data\":\\{\"id\":\\d+\\}\\}");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("正则匹配失败时应为 false")
        void regexNotMatches() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "REGEX", null, "\\{\"error\":");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("无效正则表达式应返回 false")
        void invalidRegexShouldReturnFalse() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "REGEX", null, "[invalid");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }

        @Test
        @DisplayName("expectedValue 为 null 时 REGEX 应为 false")
        void nullExpectedRegexShouldReturnFalse() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_BODY", "REGEX", null, null);
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, "{\"data\":{\"id\":1}}", 0L);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("execute — 多条断言混合")
    class ExecuteMultipleAssertions {

        @Test
        @DisplayName("多条断言同时执行，结果数量应与断言数量一致")
        void multipleAssertionsCount() {
            List<AssertionRule> rules = Arrays.asList(
                    new AssertionRule("r1", "RESPONSE_STATUS", "EQUALS", null, "200"),
                    new AssertionRule("r2", "RESPONSE_BODY", "CONTAINS", null, "data"),
                    new AssertionRule("r3", "RESPONSE_TIME", "GT", null, "0")
            );
            List<Map<String, Object>> result = assertionExecutor.execute(
                    rules, 200, null, "{\"data\":{\"id\":1}}", 156L);
            assertThat(result).hasSize(3);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
            assertThat(result.get(1).get("passed")).isEqualTo(true);
            assertThat(result.get(2).get("passed")).isEqualTo(true);
        }

        @Test
        @DisplayName("混合通过/失败的断言")
        void mixedPassFail() {
            List<AssertionRule> rules = Arrays.asList(
                    new AssertionRule("r1", "RESPONSE_STATUS", "EQUALS", null, "200"),
                    new AssertionRule("r2", "RESPONSE_BODY", "CONTAINS", null, "error"),
                    new AssertionRule("r3", "RESPONSE_TIME", "LT", null, "100")
            );
            List<Map<String, Object>> result = assertionExecutor.execute(
                    rules, 200, null, "{\"data\":{\"id\":1}}", 156L);
            assertThat(result).hasSize(3);
            assertThat(result.get(0).get("passed")).isEqualTo(true);
            assertThat(result.get(1).get("passed")).isEqualTo(false);
            assertThat(result.get(2).get("passed")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("execute — 不支持的比较类型")
    class ExecuteUnsupportedCompareType {

        @Test
        @DisplayName("不支持的比较类型应返回 false")
        void unsupportedCompareType() {
            AssertionRule rule = new AssertionRule("test", "RESPONSE_STATUS", "UNKNOWN", null, "200");
            List<Map<String, Object>> result = assertionExecutor.execute(
                    Collections.singletonList(rule), 200, null, null, 0L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("passed")).isEqualTo(false);
        }
    }
}