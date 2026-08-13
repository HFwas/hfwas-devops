package com.hfwas.devops.apitest.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * HTTP 请求方式枚举
 *
 * @author hfwas
 */
@Getter
@RequiredArgsConstructor
public enum HttpMethodEnum {

    GET("GET", "获取资源"),
    POST("POST", "创建资源"),
    PUT("PUT", "全量更新"),
    PATCH("PATCH", "部分更新"),
    DELETE("DELETE", "删除资源"),
    HEAD("HEAD", "获取响应头"),
    OPTIONS("OPTIONS", "预检请求");

    private final String code;
    private final String label;
}