package com.hfwas.devops.apitest.common;

import com.hfwas.devops.apitest.common.enums.ApiStatusEnum;
import com.hfwas.devops.apitest.common.enums.AssertionSourceEnum;
import com.hfwas.devops.apitest.common.enums.CompareTypeEnum;
import com.hfwas.devops.apitest.common.enums.DebugStatusEnum;
import com.hfwas.devops.apitest.common.enums.ExtractSourceEnum;
import com.hfwas.devops.apitest.common.enums.HttpMethodEnum;
import com.hfwas.devops.apitest.common.enums.ParamDataTypeEnum;
import com.hfwas.devops.apitest.common.enums.ParamTypeEnum;
import com.hfwas.devops.apitest.common.enums.ScriptTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 枚举值完整性测试
 * <p>
 * 验证所有枚举的常量数量、code/label 取值、fromCode 解析逻辑及边界情况。
 *
 * @author hfwas
 */
@DisplayName("枚举值完整性测试")
class EnumTest {

    // ========================================================================
    // ApiStatusEnum
    // ========================================================================

    @Nested
    @DisplayName("ApiStatusEnum — 接口状态枚举")
    class ApiStatusEnumTest {

        @Test
        @DisplayName("应包含 3 个枚举常量")
        void shouldHaveThreeConstants() {
            assertEquals(3, ApiStatusEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("DRAFT", ApiStatusEnum.DRAFT.getCode());
            assertEquals("草稿", ApiStatusEnum.DRAFT.getLabel());
            assertEquals("PUBLISHED", ApiStatusEnum.PUBLISHED.getCode());
            assertEquals("已发布", ApiStatusEnum.PUBLISHED.getLabel());
            assertEquals("DEPRECATED", ApiStatusEnum.DEPRECATED.getCode());
            assertEquals("已废弃", ApiStatusEnum.DEPRECATED.getLabel());
        }

        @Test
        @DisplayName("枚举名称应与 code 值一致")
        void enumNameShouldMatchCode() {
            for (ApiStatusEnum e : ApiStatusEnum.values()) {
                assertEquals(e.name(), e.getCode());
            }
        }
    }

    // ========================================================================
    // AssertionSourceEnum
    // ========================================================================

    @Nested
    @DisplayName("AssertionSourceEnum — 断言来源枚举")
    class AssertionSourceEnumTest {

        @Test
        @DisplayName("应包含 4 个枚举常量")
        void shouldHaveFourConstants() {
            assertEquals(4, AssertionSourceEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("RESPONSE_STATUS", AssertionSourceEnum.RESPONSE_STATUS.getCode());
            assertEquals("响应状态码", AssertionSourceEnum.RESPONSE_STATUS.getLabel());
            assertEquals("RESPONSE_HEADERS", AssertionSourceEnum.RESPONSE_HEADERS.getCode());
            assertEquals("响应头", AssertionSourceEnum.RESPONSE_HEADERS.getLabel());
            assertEquals("RESPONSE_BODY", AssertionSourceEnum.RESPONSE_BODY.getCode());
            assertEquals("响应体", AssertionSourceEnum.RESPONSE_BODY.getLabel());
            assertEquals("RESPONSE_TIME", AssertionSourceEnum.RESPONSE_TIME.getCode());
            assertEquals("响应耗时", AssertionSourceEnum.RESPONSE_TIME.getLabel());
        }

        @Test
        @DisplayName("fromCode 应返回正确的枚举常量")
        void fromCodeShouldReturnCorrectEnum() {
            assertEquals(AssertionSourceEnum.RESPONSE_STATUS, AssertionSourceEnum.fromCode("RESPONSE_STATUS"));
            assertEquals(AssertionSourceEnum.RESPONSE_HEADERS, AssertionSourceEnum.fromCode("RESPONSE_HEADERS"));
            assertEquals(AssertionSourceEnum.RESPONSE_BODY, AssertionSourceEnum.fromCode("RESPONSE_BODY"));
            assertEquals(AssertionSourceEnum.RESPONSE_TIME, AssertionSourceEnum.fromCode("RESPONSE_TIME"));
        }

        @ParameterizedTest
        @MethodSource("invalidCodeProvider")
        @DisplayName("fromCode 传入非法值应返回 null")
        void fromCodeInvalidShouldReturnNull(String code) {
            assertNull(AssertionSourceEnum.fromCode(code));
        }

        static Stream<Arguments> invalidCodeProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("  "),
                    Arguments.of("response_status"),
                    Arguments.of("RESPONSE_STATUS_EXTRA"),
                    Arguments.of("UNKNOWN")
            );
        }
    }

    // ========================================================================
    // CompareTypeEnum
    // ========================================================================

    @Nested
    @DisplayName("CompareTypeEnum — 断言比较方式枚举")
    class CompareTypeEnumTest {

        @Test
        @DisplayName("应包含 9 个枚举常量")
        void shouldHaveNineConstants() {
            assertEquals(9, CompareTypeEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("EQUALS", CompareTypeEnum.EQUALS.getCode());
            assertEquals("等于", CompareTypeEnum.EQUALS.getLabel());
            assertEquals("NOT_EQUALS", CompareTypeEnum.NOT_EQUALS.getCode());
            assertEquals("不等于", CompareTypeEnum.NOT_EQUALS.getLabel());
            assertEquals("CONTAINS", CompareTypeEnum.CONTAINS.getCode());
            assertEquals("包含", CompareTypeEnum.CONTAINS.getLabel());
            assertEquals("NOT_CONTAINS", CompareTypeEnum.NOT_CONTAINS.getCode());
            assertEquals("不包含", CompareTypeEnum.NOT_CONTAINS.getLabel());
            assertEquals("REGEX", CompareTypeEnum.REGEX.getCode());
            assertEquals("正则匹配", CompareTypeEnum.REGEX.getLabel());
            assertEquals("GT", CompareTypeEnum.GT.getCode());
            assertEquals("大于", CompareTypeEnum.GT.getLabel());
            assertEquals("GTE", CompareTypeEnum.GTE.getCode());
            assertEquals("大于等于", CompareTypeEnum.GTE.getLabel());
            assertEquals("LT", CompareTypeEnum.LT.getCode());
            assertEquals("小于", CompareTypeEnum.LT.getLabel());
            assertEquals("LTE", CompareTypeEnum.LTE.getCode());
            assertEquals("小于等于", CompareTypeEnum.LTE.getLabel());
        }

        @Test
        @DisplayName("fromCode 应返回正确的枚举常量")
        void fromCodeShouldReturnCorrectEnum() {
            assertEquals(CompareTypeEnum.EQUALS, CompareTypeEnum.fromCode("EQUALS"));
            assertEquals(CompareTypeEnum.NOT_EQUALS, CompareTypeEnum.fromCode("NOT_EQUALS"));
            assertEquals(CompareTypeEnum.CONTAINS, CompareTypeEnum.fromCode("CONTAINS"));
            assertEquals(CompareTypeEnum.NOT_CONTAINS, CompareTypeEnum.fromCode("NOT_CONTAINS"));
            assertEquals(CompareTypeEnum.REGEX, CompareTypeEnum.fromCode("REGEX"));
            assertEquals(CompareTypeEnum.GT, CompareTypeEnum.fromCode("GT"));
            assertEquals(CompareTypeEnum.GTE, CompareTypeEnum.fromCode("GTE"));
            assertEquals(CompareTypeEnum.LT, CompareTypeEnum.fromCode("LT"));
            assertEquals(CompareTypeEnum.LTE, CompareTypeEnum.fromCode("LTE"));
        }

        @ParameterizedTest
        @MethodSource("invalidCodeProvider")
        @DisplayName("fromCode 传入非法值应返回 null")
        void fromCodeInvalidShouldReturnNull(String code) {
            assertNull(CompareTypeEnum.fromCode(code));
        }

        static Stream<Arguments> invalidCodeProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("  "),
                    Arguments.of("equals"),
                    Arguments.of("UNKNOWN"),
                    Arguments.of("EQUAL")
            );
        }
    }

    // ========================================================================
    // DebugStatusEnum
    // ========================================================================

    @Nested
    @DisplayName("DebugStatusEnum — 调试状态枚举")
    class DebugStatusEnumTest {

        @Test
        @DisplayName("应包含 3 个枚举常量")
        void shouldHaveThreeConstants() {
            assertEquals(3, DebugStatusEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("SUCCESS", DebugStatusEnum.SUCCESS.getCode());
            assertEquals("成功", DebugStatusEnum.SUCCESS.getLabel());
            assertEquals("FAILURE", DebugStatusEnum.FAILURE.getCode());
            assertEquals("失败", DebugStatusEnum.FAILURE.getLabel());
            assertEquals("ERROR", DebugStatusEnum.ERROR.getCode());
            assertEquals("错误", DebugStatusEnum.ERROR.getLabel());
        }

        @Test
        @DisplayName("fromCode 应返回正确的枚举常量")
        void fromCodeShouldReturnCorrectEnum() {
            assertEquals(DebugStatusEnum.SUCCESS, DebugStatusEnum.fromCode("SUCCESS"));
            assertEquals(DebugStatusEnum.FAILURE, DebugStatusEnum.fromCode("FAILURE"));
            assertEquals(DebugStatusEnum.ERROR, DebugStatusEnum.fromCode("ERROR"));
        }

        @ParameterizedTest
        @MethodSource("invalidCodeProvider")
        @DisplayName("fromCode 传入非法值应返回 null")
        void fromCodeInvalidShouldReturnNull(String code) {
            assertNull(DebugStatusEnum.fromCode(code));
        }

        static Stream<Arguments> invalidCodeProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("  "),
                    Arguments.of("success"),
                    Arguments.of("UNKNOWN")
            );
        }
    }

    // ========================================================================
    // ExtractSourceEnum
    // ========================================================================

    @Nested
    @DisplayName("ExtractSourceEnum — 变量提取来源枚举")
    class ExtractSourceEnumTest {

        @Test
        @DisplayName("应包含 3 个枚举常量")
        void shouldHaveThreeConstants() {
            assertEquals(3, ExtractSourceEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("RESPONSE_BODY", ExtractSourceEnum.RESPONSE_BODY.getCode());
            assertEquals("响应体", ExtractSourceEnum.RESPONSE_BODY.getLabel());
            assertEquals("RESPONSE_HEADERS", ExtractSourceEnum.RESPONSE_HEADERS.getCode());
            assertEquals("响应头", ExtractSourceEnum.RESPONSE_HEADERS.getLabel());
            assertEquals("RESPONSE_STATUS", ExtractSourceEnum.RESPONSE_STATUS.getCode());
            assertEquals("响应状态码", ExtractSourceEnum.RESPONSE_STATUS.getLabel());
        }

        @Test
        @DisplayName("fromCode 应返回正确的枚举常量")
        void fromCodeShouldReturnCorrectEnum() {
            assertEquals(ExtractSourceEnum.RESPONSE_BODY, ExtractSourceEnum.fromCode("RESPONSE_BODY"));
            assertEquals(ExtractSourceEnum.RESPONSE_HEADERS, ExtractSourceEnum.fromCode("RESPONSE_HEADERS"));
            assertEquals(ExtractSourceEnum.RESPONSE_STATUS, ExtractSourceEnum.fromCode("RESPONSE_STATUS"));
        }

        @ParameterizedTest
        @MethodSource("invalidCodeProvider")
        @DisplayName("fromCode 传入非法值应返回 null")
        void fromCodeInvalidShouldReturnNull(String code) {
            assertNull(ExtractSourceEnum.fromCode(code));
        }

        static Stream<Arguments> invalidCodeProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("  "),
                    Arguments.of("response_body"),
                    Arguments.of("UNKNOWN")
            );
        }

        @Test
        @DisplayName("AssertionSourceEnum 和 ExtractSourceEnum 共享的 code 应语义一致")
        void sharedCodesShouldBeConsistent() {
            assertEquals(
                    AssertionSourceEnum.RESPONSE_BODY.getCode(),
                    ExtractSourceEnum.RESPONSE_BODY.getCode()
            );
            assertEquals(
                    AssertionSourceEnum.RESPONSE_HEADERS.getCode(),
                    ExtractSourceEnum.RESPONSE_HEADERS.getCode()
            );
            assertEquals(
                    AssertionSourceEnum.RESPONSE_STATUS.getCode(),
                    ExtractSourceEnum.RESPONSE_STATUS.getCode()
            );
        }
    }

    // ========================================================================
    // HttpMethodEnum
    // ========================================================================

    @Nested
    @DisplayName("HttpMethodEnum — HTTP 请求方式枚举")
    class HttpMethodEnumTest {

        @Test
        @DisplayName("应包含 7 个枚举常量")
        void shouldHaveSevenConstants() {
            assertEquals(7, HttpMethodEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("GET", HttpMethodEnum.GET.getCode());
            assertEquals("获取资源", HttpMethodEnum.GET.getLabel());
            assertEquals("POST", HttpMethodEnum.POST.getCode());
            assertEquals("创建资源", HttpMethodEnum.POST.getLabel());
            assertEquals("PUT", HttpMethodEnum.PUT.getCode());
            assertEquals("全量更新", HttpMethodEnum.PUT.getLabel());
            assertEquals("PATCH", HttpMethodEnum.PATCH.getCode());
            assertEquals("部分更新", HttpMethodEnum.PATCH.getLabel());
            assertEquals("DELETE", HttpMethodEnum.DELETE.getCode());
            assertEquals("删除资源", HttpMethodEnum.DELETE.getLabel());
            assertEquals("HEAD", HttpMethodEnum.HEAD.getCode());
            assertEquals("获取响应头", HttpMethodEnum.HEAD.getLabel());
            assertEquals("OPTIONS", HttpMethodEnum.OPTIONS.getCode());
            assertEquals("预检请求", HttpMethodEnum.OPTIONS.getLabel());
        }

        @Test
        @DisplayName("枚举名称应与 code 值一致")
        void enumNameShouldMatchCode() {
            for (HttpMethodEnum e : HttpMethodEnum.values()) {
                assertEquals(e.name(), e.getCode());
            }
        }

        @Test
        @DisplayName("valueOf 应能正确解析所有枚举常量")
        void valueOfShouldResolveAllConstants() {
            assertEquals(HttpMethodEnum.GET, HttpMethodEnum.valueOf("GET"));
            assertEquals(HttpMethodEnum.POST, HttpMethodEnum.valueOf("POST"));
            assertEquals(HttpMethodEnum.PUT, HttpMethodEnum.valueOf("PUT"));
            assertEquals(HttpMethodEnum.PATCH, HttpMethodEnum.valueOf("PATCH"));
            assertEquals(HttpMethodEnum.DELETE, HttpMethodEnum.valueOf("DELETE"));
            assertEquals(HttpMethodEnum.HEAD, HttpMethodEnum.valueOf("HEAD"));
            assertEquals(HttpMethodEnum.OPTIONS, HttpMethodEnum.valueOf("OPTIONS"));
        }

        @Test
        @DisplayName("valueOf 传入非法名称应抛出异常")
        void valueOfInvalidShouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> HttpMethodEnum.valueOf("get"));
            assertThrows(IllegalArgumentException.class, () -> HttpMethodEnum.valueOf("TRACE"));
        }

        @Test
        @DisplayName("valueOf 传入 null 应抛出 NullPointerException")
        void valueOfNullShouldThrow() {
            assertThrows(NullPointerException.class, () -> HttpMethodEnum.valueOf(null));
        }
    }

    // ========================================================================
    // ParamDataTypeEnum
    // ========================================================================

    @Nested
    @DisplayName("ParamDataTypeEnum — 参数数据类型枚举")
    class ParamDataTypeEnumTest {

        @Test
        @DisplayName("应包含 7 个枚举常量")
        void shouldHaveSevenConstants() {
            assertEquals(7, ParamDataTypeEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("string", ParamDataTypeEnum.STRING.getCode());
            assertEquals("字符串", ParamDataTypeEnum.STRING.getLabel());
            assertEquals("integer", ParamDataTypeEnum.INTEGER.getCode());
            assertEquals("整数", ParamDataTypeEnum.INTEGER.getLabel());
            assertEquals("number", ParamDataTypeEnum.NUMBER.getCode());
            assertEquals("浮点数", ParamDataTypeEnum.NUMBER.getLabel());
            assertEquals("boolean", ParamDataTypeEnum.BOOLEAN.getCode());
            assertEquals("布尔值", ParamDataTypeEnum.BOOLEAN.getLabel());
            assertEquals("array", ParamDataTypeEnum.ARRAY.getCode());
            assertEquals("数组", ParamDataTypeEnum.ARRAY.getLabel());
            assertEquals("object", ParamDataTypeEnum.OBJECT.getCode());
            assertEquals("对象", ParamDataTypeEnum.OBJECT.getLabel());
            assertEquals("file", ParamDataTypeEnum.FILE.getCode());
            assertEquals("文件", ParamDataTypeEnum.FILE.getLabel());
        }

        @Test
        @DisplayName("枚举名称应与 code 值不同（小写 code 与大写 name 的差异）")
        void enumNameDiffersFromCode() {
            for (ParamDataTypeEnum e : ParamDataTypeEnum.values()) {
                assertNotEquals(e.name(), e.getCode());
                assertEquals(e.name().toLowerCase(), e.getCode());
            }
        }
    }

    // ========================================================================
    // ParamTypeEnum
    // ========================================================================

    @Nested
    @DisplayName("ParamTypeEnum — 参数类型枚举")
    class ParamTypeEnumTest {

        @Test
        @DisplayName("应包含 4 个枚举常量")
        void shouldHaveFourConstants() {
            assertEquals(4, ParamTypeEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("path", ParamTypeEnum.PATH.getCode());
            assertEquals("路径参数", ParamTypeEnum.PATH.getLabel());
            assertEquals("query", ParamTypeEnum.QUERY.getCode());
            assertEquals("Query参数", ParamTypeEnum.QUERY.getLabel());
            assertEquals("header", ParamTypeEnum.HEADER.getCode());
            assertEquals("请求头", ParamTypeEnum.HEADER.getLabel());
            assertEquals("body", ParamTypeEnum.BODY.getCode());
            assertEquals("请求体", ParamTypeEnum.BODY.getLabel());
        }

        @Test
        @DisplayName("枚举名称应与 code 值不同（小写 code 与首字母大写 name 的差异）")
        void enumNameDiffersFromCode() {
            for (ParamTypeEnum e : ParamTypeEnum.values()) {
                assertNotEquals(e.name(), e.getCode());
                assertEquals(e.name().toLowerCase(), e.getCode());
            }
        }
    }

    // ========================================================================
    // ScriptTypeEnum
    // ========================================================================

    @Nested
    @DisplayName("ScriptTypeEnum — 脚本类型枚举")
    class ScriptTypeEnumTest {

        @Test
        @DisplayName("应包含 2 个枚举常量")
        void shouldHaveTwoConstants() {
            assertEquals(2, ScriptTypeEnum.values().length);
        }

        @Test
        @DisplayName("常量的 code 和 label 应与预期一致")
        void constantsShouldMatchExpected() {
            assertEquals("PRE_REQUEST", ScriptTypeEnum.PRE_REQUEST.getCode());
            assertEquals("前置脚本", ScriptTypeEnum.PRE_REQUEST.getLabel());
            assertEquals("POST_RESPONSE", ScriptTypeEnum.POST_RESPONSE.getCode());
            assertEquals("后置脚本", ScriptTypeEnum.POST_RESPONSE.getLabel());
        }

        @Test
        @DisplayName("fromCode 应返回正确的枚举常量")
        void fromCodeShouldReturnCorrectEnum() {
            assertEquals(ScriptTypeEnum.PRE_REQUEST, ScriptTypeEnum.fromCode("PRE_REQUEST"));
            assertEquals(ScriptTypeEnum.POST_RESPONSE, ScriptTypeEnum.fromCode("POST_RESPONSE"));
        }

        @ParameterizedTest
        @MethodSource("invalidCodeProvider")
        @DisplayName("fromCode 传入非法值应返回 null")
        void fromCodeInvalidShouldReturnNull(String code) {
            assertNull(ScriptTypeEnum.fromCode(code));
        }

        static Stream<Arguments> invalidCodeProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("  "),
                    Arguments.of("pre_request"),
                    Arguments.of("UNKNOWN"),
                    Arguments.of("AFTER_RESPONSE")
            );
        }

        @Test
        @DisplayName("枚举名称应与 code 值一致")
        void enumNameShouldMatchCode() {
            for (ScriptTypeEnum e : ScriptTypeEnum.values()) {
                assertEquals(e.name(), e.getCode());
            }
        }
    }
}