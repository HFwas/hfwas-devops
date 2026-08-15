package com.hfwas.devops.apitest.common.enums;

/**
 * 脚本类型枚举
 *
 * @author hfwas
 */
public enum ScriptTypeEnum {

    /** 前置脚本（请求发送前执行） */
    PRE_REQUEST("PRE_REQUEST", "前置脚本"),

    /** 后置脚本（响应接收后执行） */
    POST_RESPONSE("POST_RESPONSE", "后置脚本");

    private final String code;
    private final String label;

    ScriptTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ScriptTypeEnum fromCode(String code) {
        for (ScriptTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}