package com.hfwas.devops.apitest.common.enums;

/**
 * 断言来源枚举
 *
 * @author hfwas
 */
public enum AssertionSourceEnum {

    /** 响应状态码 */
    RESPONSE_STATUS("RESPONSE_STATUS", "响应状态码"),

    /** 响应头 */
    RESPONSE_HEADERS("RESPONSE_HEADERS", "响应头"),

    /** 响应体 */
    RESPONSE_BODY("RESPONSE_BODY", "响应体"),

    /** 响应耗时 */
    RESPONSE_TIME("RESPONSE_TIME", "响应耗时");

    private final String code;
    private final String label;

    AssertionSourceEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AssertionSourceEnum fromCode(String code) {
        for (AssertionSourceEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}