package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.script.PreRequestScriptExecutor;
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
 * PreRequestScriptExecutor — 前置脚本执行器单元测试
 * <p>
 * 验证对 ScriptSandbox 的正确委托调用。
 *
 * @author hfwas
 */
@DisplayName("PreRequestScriptExecutor — 前置脚本执行器")
@ExtendWith(MockitoExtension.class)
class PreRequestScriptExecutorTest {

    @Mock
    private ScriptSandbox scriptSandbox;

    @InjectMocks
    private PreRequestScriptExecutor preRequestScriptExecutor;

    @Nested
    @DisplayName("execute — 委托调用")
    class ExecuteDelegation {

        @Test
        @DisplayName("执行有效脚本应返回正常结果")
        void validScript() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://localhost/api/test");
            expected.setMethod("GET");
            expected.setHeaders(new HashMap<>());
            expected.setLogs(List.of("console.log executed"));

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "console.log('test');", "http://localhost/api/test", "GET",
                    new HashMap<>(), null, new HashMap<>());

            assertThat(result).isNotNull();
            assertThat(result.getUrl()).isEqualTo("http://localhost/api/test");
            assertThat(result.getMethod()).isEqualTo("GET");
            assertThat(result.getLogs()).contains("console.log executed");
        }

        @Test
        @DisplayName("脚本修改 URL 应反映在结果中")
        void scriptModifyUrl() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://modified/api/test");
            expected.setMethod("GET");
            expected.setHeaders(new HashMap<>());
            expected.setLogs(List.of());

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "pm.request.url = 'http://modified/api/test';",
                    "http://localhost/api/test", "GET",
                    new HashMap<>(), null, new HashMap<>());

            assertThat(result.getUrl()).isEqualTo("http://modified/api/test");
        }

        @Test
        @DisplayName("脚本修改 Headers 应反映在结果中")
        void scriptModifyHeaders() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://localhost/api/test");
            expected.setMethod("GET");
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer token123");
            expected.setHeaders(headers);
            expected.setLogs(List.of());

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "pm.request.headers.set('Authorization', 'Bearer token123');",
                    "http://localhost/api/test", "GET",
                    new HashMap<>(), null, new HashMap<>());

            assertThat(result.getHeaders()).containsEntry("Authorization", "Bearer token123");
        }

        @Test
        @DisplayName("脚本修改 Body 应反映在结果中")
        void scriptModifyBody() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://localhost/api/test");
            expected.setMethod("POST");
            expected.setHeaders(new HashMap<>());
            expected.setBody("{\"modified\":true}");
            expected.setLogs(List.of());

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "pm.request.body = '{\"modified\":true}';",
                    "http://localhost/api/test", "POST",
                    new HashMap<>(), "{\"original\":true}", new HashMap<>());

            assertThat(result.getBody()).isEqualTo("{\"modified\":true}");
        }

        @Test
        @DisplayName("脚本设置环境变量应反映在结果中")
        void scriptSetEnvironmentVariable() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://localhost/api/test");
            expected.setMethod("GET");
            expected.setHeaders(new HashMap<>());
            Map<String, String> envVars = new HashMap<>();
            envVars.put("apiKey", "abc-123");
            expected.setEnvironmentVariables(envVars);
            expected.setLogs(List.of());

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "pm.environment.set('apiKey', 'abc-123');",
                    "http://localhost/api/test", "GET",
                    new HashMap<>(), null, new HashMap<>());

            assertThat(result.getEnvironmentVariables()).containsEntry("apiKey", "abc-123");
        }

        @Test
        @DisplayName("脚本语法错误应返回错误信息")
        void scriptSyntaxError() {
            ScriptSandbox.PreRequestResult expected = new ScriptSandbox.PreRequestResult();
            expected.setUrl("http://localhost/api/test");
            expected.setMethod("GET");
            expected.setHeaders(new HashMap<>());
            expected.setError("SyntaxError: Unexpected token");
            expected.setLogs(List.of());

            when(scriptSandbox.executePreRequest(anyString(), anyString(), anyString(),
                    anyMap(), any(), anyMap())).thenReturn(expected);

            ScriptSandbox.PreRequestResult result = preRequestScriptExecutor.execute(
                    "invalid syntax{{{",
                    "http://localhost/api/test", "GET",
                    new HashMap<>(), null, new HashMap<>());

            assertThat(result.getError()).isNotNull();
        }
    }
}