package com.hfwas.devops.apitest.debugger.model;

import lombok.Data;

import java.util.Map;

/**
 * 调试请求模型
 *
 * @author hfwas
 */
@Data
public class DebugRequest {

    /** 请求URL（变量已渲染） */
    private String url;

    /** 请求方式 */
    private String method;

    /** 请求头 */
    private Map<String, String> headers;

    /** Query参数 */
    private Map<String, String> queryParams;

    /** 请求体 */
    private String body;

    /** 请求Content-Type */
    private String contentType;

    /** 超时时间（毫秒） */
    private Long timeoutMs;

    /** 是否跟随重定向 */
    private Boolean followRedirects;
}