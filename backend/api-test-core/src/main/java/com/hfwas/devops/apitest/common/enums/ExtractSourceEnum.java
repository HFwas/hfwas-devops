package com.hfwas.devops.apitest.common.enums;

/**
 * 变量提取来源枚举
 *
 * @author hfwas
 */
public enum ExtractSourceEnum {

    /** 响应体 */
    RESPONSE_BODY("RESPONSE_BODY", "响应体"),

    /** 响应头 */
    RESPONSE_HEADERS("RESPONSE_HEADERS", "响应头"),

    /** 响应状态码 */
    RESPONSE_STATUS("RESPONSE_STATUS", "响应状态码");

    private final String code;
    private final String label;

    ExtractSourceEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ExtractSourceEnum fromCode(String code) {
        for (ExtractSourceEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}