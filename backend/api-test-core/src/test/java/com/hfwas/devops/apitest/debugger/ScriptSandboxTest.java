package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.script.ScriptSandbox;
import com.hfwas.devops.apitest.debugger.script.ScriptSandbox.PostResponseResult;
import com.hfwas.devops.apitest.debugger.script.ScriptSandbox.PreRequestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScriptSandbox 集成测试
 * <p>
 * 基于 GraalVM JavaScript 引擎的真实执行测试。
 * 验证 pm.request / pm.response / pm.environment / pm.variables 注入和脚本执行。
 */
@ExtendWith(MockitoExtension.class)
class ScriptSandboxTest {

    @InjectMocks
    private ScriptSandbox scriptSandbox;

    @Test
    void executePreRequest_validScript() {
        PreRequestResult result = scriptSandbox.executePreRequest(
                "console.log('hello');",
                "http://localhost", "GET",
                new HashMap<>(), null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getUrl()).isEqualTo("http://localhost");
        assertThat(result.getMethod()).isEqualTo("GET");
        assertThat(result.getLogs()).anyMatch(log -> log.contains("hello"));
    }

    @Test
    void executePreRequest_modifyUrl() {
        PreRequestResult result = scriptSandbox.executePreRequest(
                "pm.request.url = 'http://modified/path';",
                "http://localhost", "GET",
                new HashMap<>(), null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getUrl()).isEqualTo("http://modified/path");
    }

    @Test
    void executePreRequest_syntaxError() {
        PreRequestResult result = scriptSandbox.executePreRequest(
                "invalid syntax{{{",
                "http://localhost", "GET",
                new HashMap<>(), null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNotNull();
    }

    @Test
    void executePreRequest_modifyHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        PreRequestResult result = scriptSandbox.executePreRequest(
                "pm.request.headers.set('Authorization', 'Bearer token123');",
                "http://localhost", "GET",
                headers, null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getHeaders())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Authorization", "Bearer token123");
    }

    @Test
    void executePreRequest_setEnvironmentVariable() {
        PreRequestResult result = scriptSandbox.executePreRequest(
                "pm.environment.set('apiKey', 'abc-123');",
                "http://localhost", "GET",
                new HashMap<>(), null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getEnvironmentVariables()).containsEntry("apiKey", "abc-123");
    }

    @Test
    void executePostResponse_validScript() {
        PostResponseResult result = scriptSandbox.executePostResponse(
                "console.log('done');",
                200, new HashMap<>(), null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getLogs()).anyMatch(log -> log.contains("done"));
    }

    @Test
    void executePostResponse_parseVariables() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("baseUrl", "http://example.com");

        PostResponseResult result = scriptSandbox.executePostResponse(
                "pm.environment.set('token', 'abc123');" +
                "console.log('token set');",
                200, new HashMap<>(), "{\"id\":1}", envVars);

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getEnvironmentVariables())
                .containsEntry("baseUrl", "http://example.com")
                .containsEntry("token", "abc123");
        assertThat(result.getLogs()).anyMatch(log -> log.contains("token set"));
    }

    @Test
    void executePostResponse_readResponseBody() {
        PostResponseResult result = scriptSandbox.executePostResponse(
                "console.log('status: ' + pm.response.statusCode);" +
                "console.log('body: ' + pm.response.body);",
                200, new HashMap<>(), "{\"name\":\"test\"}", new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getLogs()).anyMatch(log -> log.contains("status: 200"));
        assertThat(result.getLogs()).anyMatch(log -> log.contains("test"));
    }

    @Test
    void executePostResponse_setVariables() {
        PostResponseResult result = scriptSandbox.executePostResponse(
                "pm.variables.set('extracted', 'value123');",
                200, new HashMap<>(), "{}", new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getExtractedVariables()).containsEntry("extracted", "value123");
    }

    @Test
    void executePreRequest_nullHeaders() {
        PreRequestResult result = scriptSandbox.executePreRequest(
                "console.log('test');",
                "http://localhost", "GET",
                null, null, new HashMap<>());

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getHeaders()).isNotNull();
    }

    @Test
    void executePreRequest_readEnvironmentVariable() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("baseUrl", "http://example.com");

        PreRequestResult result = scriptSandbox.executePreRequest(
                "console.log('baseUrl: ' + pm.environment.get('baseUrl'));",
                "http://localhost", "GET",
                new HashMap<>(), null, envVars);

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getLogs()).anyMatch(log -> log.contains("baseUrl: http://example.com"));
    }

    @Test
    void executePreRequest_unsetEnvironmentVariable() {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("baseUrl", "http://example.com");

        PreRequestResult result = scriptSandbox.executePreRequest(
                "pm.environment.unset('baseUrl');",
                "http://localhost", "GET",
                new HashMap<>(), null, envVars);

        assertThat(result).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getEnvironmentVariables()).doesNotContainKey("baseUrl");
    }
}