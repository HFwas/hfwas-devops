package com.hfwas.devops.apitest.curl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * cURL 解析结果 VO
 *
 * @author hfwas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurlParseResultVO {

    /** 请求 URL */
    private String url;

    /** 请求方法（GET, POST, PUT, DELETE, PATCH 等） */
    private String method;

    /** 请求头 */
    private Map<String, String> headers;

    /** 请求体 */
    private String body;

    /** Content-Type */
    private String contentType;

    /** 是否跟随重定向 */
    private Boolean followRedirects;

    /** 超时时间（毫秒） */
    private Long timeoutMs;

    /** 解析警告信息 */
    private List<String> warnings;
}