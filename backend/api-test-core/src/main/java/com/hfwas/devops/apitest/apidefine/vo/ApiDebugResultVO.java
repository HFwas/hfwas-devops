package com.hfwas.devops.apitest.apidefine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 调试结果 VO
 *
 * @author hfwas
 */
@Data
@Schema(description = "调试结果")
public class ApiDebugResultVO {

    @Schema(description = "调试历史ID")
    private Long historyId;

    // ===== 请求报文 =====
    @Schema(description = "最终请求URL（变量已渲染）")
    private String requestUrl;

    @Schema(description = "请求方式")
    private String requestMethod;

    @Schema(description = "请求头")
    private Map<String, String> requestHeaders;

    @Schema(description = "请求Query参数")
    private Map<String, String> requestQuery;

    @Schema(description = "请求体")
    private String requestBody;

    @Schema(description = "请求Content-Type")
    private String requestContentType;

    // ===== 响应报文 =====
    @Schema(description = "响应状态码")
    private Integer responseStatusCode;

    @Schema(description = "响应头")
    private Map<String, String> responseHeaders;

    @Schema(description = "响应体")
    private String responseBody;

    @Schema(description = "响应Content-Type")
    private String responseContentType;

    @Schema(description = "响应体大小（字节）")
    private Long responseSize;

    // ===== 调试信息 =====
    @Schema(description = "请求耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "调试状态 SUCCESS / FAILURE / ERROR")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    // ===== 脚本日志 =====
    @Schema(description = "前置脚本执行日志")
    private List<String> preRequestLogs;

    @Schema(description = "后置脚本执行日志")
    private List<String> postResponseLogs;

    // ===== 断言结果 =====
    @Schema(description = "断言结果列表")
    private List<Map<String, Object>> assertionResults;

    @Schema(description = "断言是否全部通过")
    private Boolean allAssertionsPassed;

    // ===== 提取变量 =====
    @Schema(description = "提取的变量快照")
    private Map<String, String> extractedVariables;
}