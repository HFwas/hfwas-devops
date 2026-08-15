package com.hfwas.devops.apitest.common;

import com.hfwas.devops.apitest.common.exception.ApiTestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiTestException 异常类测试
 * <p>
 * 验证三个构造函数的正确性、code 默认值、消息传递及链式异常行为。
 *
 * @author hfwas
 */
@DisplayName("ApiTestException — 接口测试模块异常测试")
class ApiTestExceptionTest {

    // ========================================================================
    // 构造函数：ApiTestException(String message)
    // ========================================================================

    @Nested
    @DisplayName("构造函数：ApiTestException(String message)")
    class ConstructorWithMessageOnly {

        @Test
        @DisplayName("默认 code 应为 400")
        void defaultCodeShouldBe400() {
            ApiTestException ex = new ApiTestException("发生错误");
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("message 应与传入参数一致")
        void messageShouldMatchInput() {
            ApiTestException ex = new ApiTestException("发生错误");
            assertEquals("发生错误", ex.getMessage());
        }

        @Test
        @DisplayName("cause 应为 null")
        void causeShouldBeNull() {
            ApiTestException ex = new ApiTestException("发生错误");
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("传入 null message 时 getMessage 应返回 null")
        void nullMessageShouldReturnNull() {
            ApiTestException ex = new ApiTestException((String) null);
            assertNull(ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("传入空字符串 message 应正常创建")
        void emptyMessageShouldBeAllowed() {
            ApiTestException ex = new ApiTestException("");
            assertEquals("", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("异常应为 RuntimeException 子类")
        void shouldBeRuntimeException() {
            ApiTestException ex = new ApiTestException("发生错误");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    // ========================================================================
    // 构造函数：ApiTestException(Integer code, String message)
    // ========================================================================

    @Nested
    @DisplayName("构造函数：ApiTestException(Integer code, String message)")
    class ConstructorWithCodeAndMessage {

        @Test
        @DisplayName("code 应与传入参数一致")
        void codeShouldMatchInput() {
            ApiTestException ex = new ApiTestException(403, "无权限访问");
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("message 应与传入参数一致")
        void messageShouldMatchInput() {
            ApiTestException ex = new ApiTestException(403, "无权限访问");
            assertEquals("无权限访问", ex.getMessage());
        }

        @Test
        @DisplayName("cause 应为 null")
        void causeShouldBeNull() {
            ApiTestException ex = new ApiTestException(403, "无权限访问");
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("传入 code=0 应正常创建")
        void zeroCodeShouldBeAllowed() {
            ApiTestException ex = new ApiTestException(0, "零值错误码");
            assertEquals(0, ex.getCode());
        }

        @Test
        @DisplayName("传入负数 code 应正常创建")
        void negativeCodeShouldBeAllowed() {
            ApiTestException ex = new ApiTestException(-1, "负数错误码");
            assertEquals(-1, ex.getCode());
        }

        @Test
        @DisplayName("传入大数值 code 应正常创建")
        void largeCodeShouldBeAllowed() {
            ApiTestException ex = new ApiTestException(99999, "大数值错误码");
            assertEquals(99999, ex.getCode());
        }
    }

    // ========================================================================
    // 构造函数：ApiTestException(Integer code, String message, Throwable cause)
    // ========================================================================

    @Nested
    @DisplayName("构造函数：ApiTestException(Integer code, String message, Throwable cause)")
    class ConstructorWithCodeMessageAndCause {

        @Test
        @DisplayName("code 应与传入参数一致")
        void codeShouldMatchInput() {
            ApiTestException ex = new ApiTestException(500, "服务器内部错误", new RuntimeException("原始异常"));
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("message 应与传入参数一致")
        void messageShouldMatchInput() {
            ApiTestException ex = new ApiTestException(500, "服务器内部错误", new RuntimeException("原始异常"));
            assertEquals("服务器内部错误", ex.getMessage());
        }

        @Test
        @DisplayName("cause 应与传入的原始异常一致")
        void causeShouldMatchInput() {
            RuntimeException cause = new RuntimeException("原始异常");
            ApiTestException ex = new ApiTestException(500, "服务器内部错误", cause);
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("cause 的 message 应与原始异常一致")
        void causeMessageShouldMatchInput() {
            RuntimeException cause = new RuntimeException("原始异常");
            ApiTestException ex = new ApiTestException(500, "服务器内部错误", cause);
            assertEquals("原始异常", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("传入 null cause 应正常创建")
        void nullCauseShouldBeAllowed() {
            ApiTestException ex = new ApiTestException(500, "服务器内部错误", null);
            assertEquals(500, ex.getCode());
            assertEquals("服务器内部错误", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("传入嵌套 cause 应保留完整异常链")
        void nestedCauseShouldBePreserved() {
            IllegalArgumentException inner = new IllegalArgumentException("参数错误");
            IllegalStateException middle = new IllegalStateException("状态错误", inner);
            ApiTestException ex = new ApiTestException(422, "业务校验失败", middle);

            assertSame(middle, ex.getCause());
            assertSame(inner, ex.getCause().getCause());
            assertEquals("状态错误", ex.getCause().getMessage());
            assertEquals("参数错误", ex.getCause().getCause().getMessage());
        }
    }

    // ========================================================================
    // 跨构造函数行为一致性
    // ========================================================================

    @Nested
    @DisplayName("跨构造函数行为一致性")
    class CrossConstructorConsistency {

        @Test
        @DisplayName("所有构造函数创建的异常都应是 RuntimeException 子类")
        void allConstructorsShouldProduceRuntimeException() {
            assertInstanceOf(RuntimeException.class, new ApiTestException("错误"));
            assertInstanceOf(RuntimeException.class, new ApiTestException(400, "错误"));
            assertInstanceOf(RuntimeException.class, new ApiTestException(400, "错误", new Exception()));
        }

        @Test
        @DisplayName("code 400 的两种构造方式应一致")
        void code400ShouldBeConsistent() {
            ApiTestException ex1 = new ApiTestException("错误");
            ApiTestException ex2 = new ApiTestException(400, "错误");

            assertEquals(ex1.getCode(), ex2.getCode());
            assertEquals(ex1.getMessage(), ex2.getMessage());
        }
    }
}