package com.hfwas.devops.apitest.debugger.script;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 后置脚本执行器
 *
 * @author hfwas
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostResponseScriptExecutor {

    private final ScriptSandbox scriptSandbox;

    /**
     * 执行后置脚本
     */
    public ScriptSandbox.PostResponseResult execute(String script,
                                                     Integer statusCode,
                                                     Map<String, String> responseHeaders,
                                                     String responseBody,
                                                     Map<String, String> environmentVariables) {
        return scriptSandbox.executePostResponse(script, statusCode, responseHeaders, responseBody, environmentVariables);
    }
}