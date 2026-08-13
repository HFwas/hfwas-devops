package com.hfwas.devops.apitest.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 参数数据类型枚举
 *
 * @author hfwas
 */
@Getter
@RequiredArgsConstructor
public enum ParamDataTypeEnum {

    STRING("string", "字符串"),
    INTEGER("integer", "整数"),
    NUMBER("number", "浮点数"),
    BOOLEAN("boolean", "布尔值"),
    ARRAY("array", "数组"),
    OBJECT("object", "对象"),
    FILE("file", "文件");

    private final String code;
    private final String label;
}