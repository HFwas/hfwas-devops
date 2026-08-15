package com.hfwas.devops.apitest.debugger.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JS 脚本沙箱（基于 GraalVM JavaScript）
 * <p>
 * 提供隔离的 JavaScript 执行环境，用于执行前置脚本和后置脚本。
 * 注入 pm.request / pm.response / pm.environment 等全局对象。
 * 安全限制：禁止网络访问、文件系统操作、线程创建。
 *
 * @author hfwas
 */
@Slf4j
@Component
public class ScriptSandbox {

    /** 脚本执行超时时间（毫秒） */
    private static final long SCRIPT_TIMEOUT_MS = 5000;

    /**
     * 执行前置脚本（请求发送前）
     * <p>
     * 注入 pm.request / pm.environment / pm.variables 对象，
     * 脚本可修改请求参数和环境变量。
     *
     * @param script                脚本内容
     * @param requestUrl            请求URL（可修改）
     * @param requestMethod         请求方法
     * @param requestHeaders        请求头（可修改）
     * @param requestBody           请求体（可修改）
     * @param environmentVariables  环境变量（可读写）
     * @return 修改后的请求参数
     */
    public PreRequestResult executePreRequest(String script,
                                              String requestUrl,
                                              String requestMethod,
                                              Map<String, String> requestHeaders,
                                              String requestBody,
                                              Map<String, String> environmentVariables) {
        PreRequestResult result = new PreRequestResult();
        result.setUrl(requestUrl);
        result.setMethod(requestMethod);
        result.setHeaders(requestHeaders != null ? requestHeaders : new HashMap<>());
        result.setBody(requestBody);

        if (script == null || script.isBlank()) {
            result.setLogs(Collections.emptyList());
            return result;
        }

        try {
            String jsCode = buildPreRequestScript(
                    script, requestUrl, requestMethod,
                    requestHeaders != null ? requestHeaders : new HashMap<>(),
                    requestBody,
                    environmentVariables != null ? environmentVariables : new HashMap<>()
            );

            GraalVMResult vmResult = executeInSandbox(jsCode);

            // 读取修改后的请求参数
            if (vmResult.json.containsKey("url") && vmResult.json.getStr("url") != null) {
                result.setUrl(vmResult.json.getStr("url"));
            }
            if (vmResult.json.containsKey("method") && vmResult.json.getStr("method") != null) {
                result.setMethod(vmResult.json.getStr("method"));
            }
            if (vmResult.json.containsKey("body")) {
                result.setBody(vmResult.json.getStr("body"));
            }

            // 读取修改后的请求头
            Map<String, String> modifiedHeaders = new HashMap<>();
            Object headersRaw = vmResult.json.get("headers");
            if (headersRaw instanceof JSONObject) {
                JSONObject headersObj = (JSONObject) headersRaw;
                for (Map.Entry<String, Object> entry : headersObj.entrySet()) {
                    if (entry.getValue() != null) {
                        modifiedHeaders.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            result.setHeaders(modifiedHeaders);

            // 读取修改后的环境变量
            Map<String, String> modifiedEnv = new HashMap<>();
            Object envRaw = vmResult.json.get("env");
            if (envRaw instanceof JSONObject) {
                JSONObject envObj = (JSONObject) envRaw;
                for (Map.Entry<String, Object> entry : envObj.entrySet()) {
                    if (entry.getValue() != null) {
                        modifiedEnv.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            result.setEnvironmentVariables(modifiedEnv);

            // 设置日志
            result.setLogs(vmResult.logs);

            log.info("前置脚本执行完成: scriptLength={}, url={}", script.length(), result.getUrl());

        } catch (Exception e) {
            log.error("前置脚本执行异常", e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("timeout")) {
                msg = "脚本执行超时（" + SCRIPT_TIMEOUT_MS + "ms）";
            }
            List<String> logs = new ArrayList<>();
            logs.add("[ERROR] " + msg);
            result.setLogs(logs);
            result.setError(msg);
        }

        return result;
    }

    /**
     * 执行后置脚本（响应接收后）
     * <p>
     * 注入 pm.response / pm.environment / pm.variables 对象，
     * 脚本可读取响应并修改环境变量。
     *
     * @param script                脚本内容
     * @param responseStatusCode    响应状态码
     * @param responseHeaders       响应头
     * @param responseBody          响应体
     * @param environmentVariables  环境变量（可读写）
     * @return 后置脚本执行结果
     */
    public PostResponseResult executePostResponse(String script,
                                                  Integer responseStatusCode,
                                                  Map<String, String> responseHeaders,
                                                  String responseBody,
                                                  Map<String, String> environmentVariables) {
        PostResponseResult result = new PostResponseResult();

        if (script == null || script.isBlank()) {
            result.setLogs(Collections.emptyList());
            return result;
        }

        try {
            String jsCode = buildPostResponseScript(
                    script, responseStatusCode,
                    responseHeaders != null ? responseHeaders : new HashMap<>(),
                    responseBody,
                    environmentVariables != null ? environmentVariables : new HashMap<>()
            );

            GraalVMResult vmResult = executeInSandbox(jsCode);

            // 读取环境变量修改
            Map<String, String> modifiedEnv = new HashMap<>();
            Object envRaw = vmResult.json.get("env");
            if (envRaw instanceof JSONObject) {
                JSONObject envObj = (JSONObject) envRaw;
                for (Map.Entry<String, Object> entry : envObj.entrySet()) {
                    if (entry.getValue() != null) {
                        modifiedEnv.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            result.setEnvironmentVariables(modifiedEnv);

            // 读取提取的变量
            Map<String, String> extractedVars = new HashMap<>();
            Object varsRaw = vmResult.json.get("vars");
            if (varsRaw instanceof JSONObject) {
                JSONObject varsObj = (JSONObject) varsRaw;
                for (Map.Entry<String, Object> entry : varsObj.entrySet()) {
                    if (entry.getValue() != null) {
                        extractedVars.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            result.setExtractedVariables(extractedVars);

            // 设置日志
            result.setLogs(vmResult.logs);

            log.info("后置脚本执行完成: scriptLength={}", script.length());

        } catch (Exception e) {
            log.error("后置脚本执行异常", e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("timeout")) {
                msg = "脚本执行超时（" + SCRIPT_TIMEOUT_MS + "ms）";
            }
            List<String> logs = new ArrayList<>();
            logs.add("[ERROR] " + msg);
            result.setLogs(logs);
            result.setError(msg);
        }

        return result;
    }

    // ==================== 内部实现 ====================

    /**
     * 在 GraalVM 沙箱中执行 JS 代码
     *
     * @param jsCode 完整的 JS 代码（含 pm 对象注入 + 用户脚本 + 结果读取）
     * @return 解析后的执行结果
     */
    private GraalVMResult executeInSandbox(String jsCode) {
        try (Context context = Context.newBuilder("js")
                .allowIO(false)
                .allowHostAccess(HostAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .build()) {

            String resultJson = context.eval("js", jsCode).asString();
            JSONObject json = JSONUtil.parseObj(resultJson);

            // 解析日志
            List<String> logs = new ArrayList<>();
            Object logsRaw = json.get("logs");
            if (logsRaw instanceof cn.hutool.json.JSONArray) {
                cn.hutool.json.JSONArray logArray = (cn.hutool.json.JSONArray) logsRaw;
                for (Object logEntry : logArray) {
                    logs.add(logEntry != null ? logEntry.toString() : "");
                }
            }

            return new GraalVMResult(json, logs);
        }
    }

    /**
     * 将字符串转为 JS 字符串字面量（带引号并转义特殊字符）
     */
    private static String jsQuote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * 构建前置脚本的完整 JS 代码
     * <p>
     * 1. 注入 pm.request / pm.environment / pm.variables 对象
     * 2. 覆盖 console.log 以捕获日志
     * 3. 追加用户脚本
     * 4. 追加结果读取代码
     */
    private String buildPreRequestScript(String script,
                                         String url,
                                         String method,
                                         Map<String, String> headers,
                                         String body,
                                         Map<String, String> envVars) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("var _logs = [];\n");
        sb.append("console.log = function(msg) { _logs.push(String(msg)); };\n");
        sb.append("console.warn = function(msg) { _logs.push('[WARN] ' + String(msg)); };\n");
        sb.append("console.error = function(msg) { _logs.push('[ERROR] ' + String(msg)); };\n");

        sb.append("var pm = {\n");
        sb.append("  request: {\n");
        sb.append("    url: ").append(jsQuote(url)).append(",\n");
        sb.append("    method: ").append(jsQuote(method)).append(",\n");
        sb.append("    headers: {\n");
        sb.append("      _data: ").append(JSONUtil.toJsonStr(headers)).append(",\n");
        sb.append("      add: function(name, value) { this._data[name] = value; },\n");
        sb.append("      set: function(name, value) { this._data[name] = value; },\n");
        sb.append("      get: function(name) { return this._data[name]; },\n");
        sb.append("      delete: function(name) { delete this._data[name]; }\n");
        sb.append("    },\n");
        sb.append("    body: ").append(jsQuote(body)).append("\n");
        sb.append("  },\n");
        sb.append("  environment: {\n");
        sb.append("    _data: ").append(JSONUtil.toJsonStr(envVars)).append(",\n");
        sb.append("    get: function(key) { return this._data[key]; },\n");
        sb.append("    set: function(key, value) { this._data[key] = value; },\n");
        sb.append("    unset: function(key) { delete this._data[key]; }\n");
        sb.append("  },\n");
        sb.append("  variables: {\n");
        sb.append("    _data: {},\n");
        sb.append("    get: function(key) { return this._data[key]; },\n");
        sb.append("    set: function(key, value) { this._data[key] = value; }\n");
        sb.append("  }\n");
        sb.append("};\n");

        // 用户脚本
        sb.append(script).append("\n");

        // 结果读取
        sb.append("JSON.stringify({\n");
        sb.append("  logs: _logs,\n");
        sb.append("  url: pm.request.url,\n");
        sb.append("  method: pm.request.method,\n");
        sb.append("  headers: pm.request.headers._data,\n");
        sb.append("  body: pm.request.body,\n");
        sb.append("  env: pm.environment._data,\n");
        sb.append("  vars: pm.variables._data\n");
        sb.append("})");

        return sb.toString();
    }

    /**
     * 构建后置脚本的完整 JS 代码
     * <p>
     * 1. 注入 pm.response / pm.environment / pm.variables 对象
     * 2. 覆盖 console.log 以捕获日志
     * 3. 追加用户脚本
     * 4. 追加结果读取代码
     */
    private String buildPostResponseScript(String script,
                                           Integer statusCode,
                                           Map<String, String> headers,
                                           String body,
                                           Map<String, String> envVars) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("var _logs = [];\n");
        sb.append("console.log = function(msg) { _logs.push(String(msg)); };\n");
        sb.append("console.warn = function(msg) { _logs.push('[WARN] ' + String(msg)); };\n");
        sb.append("console.error = function(msg) { _logs.push('[ERROR] ' + String(msg)); };\n");

        // 构建响应头对象
        String headersJson = headers != null ? JSONUtil.toJsonStr(headers) : "{}";

        sb.append("var pm = {\n");
        sb.append("  response: {\n");
        sb.append("    statusCode: ").append(statusCode != null ? statusCode : "null").append(",\n");
        sb.append("    headers: ").append(headersJson).append(",\n");
        sb.append("    body: ").append(jsQuote(body)).append(",\n");
        sb.append("    responseSize: ").append(body != null ? body.length() : 0).append("\n");
        sb.append("  },\n");
        sb.append("  environment: {\n");
        sb.append("    _data: ").append(JSONUtil.toJsonStr(envVars)).append(",\n");
        sb.append("    get: function(key) { return this._data[key]; },\n");
        sb.append("    set: function(key, value) { this._data[key] = value; },\n");
        sb.append("    unset: function(key) { delete this._data[key]; }\n");
        sb.append("  },\n");
        sb.append("  variables: {\n");
        sb.append("    _data: {},\n");
        sb.append("    get: function(key) { return this._data[key]; },\n");
        sb.append("    set: function(key, value) { this._data[key] = value; }\n");
        sb.append("  }\n");
        sb.append("};\n");

        // 用户脚本
        sb.append(script).append("\n");

        // 结果读取
        sb.append("JSON.stringify({\n");
        sb.append("  logs: _logs,\n");
        sb.append("  env: pm.environment._data,\n");
        sb.append("  vars: pm.variables._data\n");
        sb.append("})");

        return sb.toString();
    }

    /**
     * GraalVM 执行结果
     */
    private static class GraalVMResult {
        private final JSONObject json;
        private final List<String> logs;

        GraalVMResult(JSONObject json, List<String> logs) {
            this.json = json;
            this.logs = logs;
        }
    }

    // ==================== 结果类 ====================

    /**
     * 前置脚本执行结果
     */
    @lombok.Data
    public static class PreRequestResult {
        private String url;
        private String method;
        private Map<String, String> headers;
        private String body;
        private Map<String, String> environmentVariables;
        private List<String> logs;
        private String error;
    }

    /**
     * 后置脚本执行结果
     */
    @lombok.Data
    public static class PostResponseResult {
        private Map<String, String> environmentVariables;
        private Map<String, String> extractedVariables;
        private List<String> logs;
        private String error;
    }
}