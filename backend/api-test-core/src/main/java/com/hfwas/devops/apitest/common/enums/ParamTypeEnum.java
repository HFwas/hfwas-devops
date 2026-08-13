package com.hfwas.devops.apitest.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 参数类型枚举
 *
 * @author hfwas
 */
@Getter
@RequiredArgsConstructor
public enum ParamTypeEnum {

    /**
     * 路径参数，如 /api/users/{id}
     */
    PATH("path", "路径参数"),

    /**
     * Query 参数，如 ?page=1&size=10
     */
    QUERY("query", "Query参数"),

    /**
     * 请求头参数
     */
    HEADER("header", "请求头"),

    /**
     * 请求体参数
     */
    BODY("body", "请求体");

    private final String code;
    private final String label;
}