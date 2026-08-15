package com.hfwas.devops.apitest.common.enums;

/**
 * 调试状态枚举
 *
 * @author hfwas
 */
public enum DebugStatusEnum {

    /** 成功（HTTP 2xx） */
    SUCCESS("SUCCESS", "成功"),

    /** 失败（HTTP 非2xx或无响应） */
    FAILURE("FAILURE", "失败"),

    /** 执行错误（网络异常、脚本异常等） */
    ERROR("ERROR", "错误");

    private final String code;
    private final String label;

    DebugStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static DebugStatusEnum fromCode(String code) {
        for (DebugStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}