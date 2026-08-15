package com.hfwas.devops.apitest.debugger.model;

import lombok.Data;

import java.util.Map;

/**
 * 调试响应模型
 *
 * @author hfwas
 */
@Data
public class DebugResponse {

    /** 响应状态码 */
    private Integer statusCode;

    /** 响应头 */
    private Map<String, String> headers;

    /** 响应体 */
    private String body;

    /** 响应Content-Type */
    private String contentType;

    /** 响应体大小（字节） */
    private Long responseSize;
}