package com.hfwas.devops.apitest.debugger.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 调试结果模型
 *
 * @author hfwas
 */
@Data
public class DebugResult {

    /** 请求（变量已渲染） */
    private DebugRequest request;

    /** 响应 */
    private DebugResponse response;

    /** 请求耗时（毫秒） */
    private Long durationMs;

    /** 调试状态 SUCCESS / FAILURE / ERROR */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 前置脚本执行日志 */
    private List<String> preRequestLogs;

    /** 后置脚本执行日志 */
    private List<String> postResponseLogs;

    /** 断言结果列表 */
    private List<Map<String, Object>> assertionResults;

    /** 断言是否全部通过 */
    private Boolean allAssertionsPassed;

    /** 提取的变量 */
    private Map<String, String> extractedVariables;
}