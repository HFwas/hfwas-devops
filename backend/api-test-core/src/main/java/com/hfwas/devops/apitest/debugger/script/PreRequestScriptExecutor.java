package com.hfwas.devops.apitest.debugger.script;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 前置脚本执行器
 *
 * @author hfwas
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreRequestScriptExecutor {

    private final ScriptSandbox scriptSandbox;

    /**
     * 执行前置脚本
     */
    public ScriptSandbox.PreRequestResult execute(String script,
                                                    String url,
                                                    String method,
                                                    Map<String, String> headers,
                                                    String body,
                                                    Map<String, String> environmentVariables) {
        return scriptSandbox.executePreRequest(script, url, method, headers, body, environmentVariables);
    }
}