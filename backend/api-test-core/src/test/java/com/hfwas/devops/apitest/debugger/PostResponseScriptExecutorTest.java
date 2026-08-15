package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.script.PostResponseScriptExecutor;
import com.hfwas.devops.apitest.debugger.script.ScriptSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * PostResponseScriptExecutor — 后置脚本执行器单元测试
 * <p>
 * 验证对 ScriptSandbox 的正确委托调用。
 *
 * @author hfwas
 */
@DisplayName("PostResponseScriptExecutor — 后置脚本执行器")
@ExtendWith(MockitoExtension.class)
class PostResponseScriptExecutorTest {

    @Mock
    private ScriptSandbox scriptSandbox;

    @InjectMocks
    private PostResponseScriptExecutor postResponseScriptExecutor;

    @Nested
    @DisplayName("execute — 委托调用")
    class ExecuteDelegation {

        @Test
        @DisplayName("执行有效脚本应返回正常结果")
        void validScript() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            expected.setLogs(List.of("post script executed"));

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "console.log('post script executed');",
                    200, new HashMap<>(), "{\"id\":1}", new HashMap<>());

            assertThat(result).isNotNull();
            assertThat(result.getLogs()).contains("post script executed");
        }

        @Test
        @DisplayName("脚本读取响应状态码")
        void scriptReadStatusCode() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            expected.setLogs(List.of("status: 200"));

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "console.log('status: ' + pm.response.statusCode);",
                    200, new HashMap<>(), "{\"id\":1}", new HashMap<>());

            assertThat(result.getLogs()).contains("status: 200");
        }

        @Test
        @DisplayName("脚本读取响应体")
        void scriptReadResponseBody() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            expected.setLogs(List.of("body: {\"id\":1}"));

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "console.log('body: ' + pm.response.body);",
                    200, new HashMap<>(), "{\"id\":1}", new HashMap<>());

            assertThat(result.getLogs()).contains("body: {\"id\":1}");
        }

        @Test
        @DisplayName("脚本设置环境变量")
        void scriptSetEnvironmentVariable() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            Map<String, String> envVars = new HashMap<>();
            envVars.put("token", "extracted-token");
            expected.setEnvironmentVariables(envVars);
            expected.setLogs(List.of());

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "pm.environment.set('token', 'extracted-token');",
                    200, new HashMap<>(), "{\"token\":\"extracted-token\"}", new HashMap<>());

            assertThat(result.getEnvironmentVariables()).containsEntry("token", "extracted-token");
        }

        @Test
        @DisplayName("脚本语法错误应返回错误信息")
        void scriptSyntaxError() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            expected.setError("SyntaxError: Unexpected token");
            expected.setLogs(List.of());

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "invalid syntax{{{",
                    200, new HashMap<>(), "{}", new HashMap<>());

            assertThat(result.getError()).isNotNull();
        }

        @Test
        @DisplayName("响应状态码为 500 时仍可正常执行脚本")
        void scriptWithErrorStatusCode() {
            ScriptSandbox.PostResponseResult expected = new ScriptSandbox.PostResponseResult();
            expected.setLogs(List.of("status: 500"));

            when(scriptSandbox.executePostResponse(anyString(), anyInt(), anyMap(),
                    anyString(), anyMap())).thenReturn(expected);

            ScriptSandbox.PostResponseResult result = postResponseScriptExecutor.execute(
                    "console.log('status: ' + pm.response.statusCode);",
                    500, new HashMap<>(), "{\"error\":\"server error\"}", new HashMap<>());

            assertThat(result.getLogs()).contains("status: 500");
        }
    }
}