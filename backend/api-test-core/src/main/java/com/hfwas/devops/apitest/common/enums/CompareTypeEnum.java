package com.hfwas.devops.apitest.common.enums;

/**
 * 断言比较方式枚举
 *
 * @author hfwas
 */
public enum CompareTypeEnum {

    EQUALS("EQUALS", "等于"),
    NOT_EQUALS("NOT_EQUALS", "不等于"),
    CONTAINS("CONTAINS", "包含"),
    NOT_CONTAINS("NOT_CONTAINS", "不包含"),
    REGEX("REGEX", "正则匹配"),
    GT("GT", "大于"),
    GTE("GTE", "大于等于"),
    LT("LT", "小于"),
    LTE("LTE", "小于等于");

    private final String code;
    private final String label;

    CompareTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CompareTypeEnum fromCode(String code) {
        for (CompareTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}