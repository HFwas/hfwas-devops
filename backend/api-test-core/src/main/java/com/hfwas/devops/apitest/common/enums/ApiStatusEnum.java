package com.hfwas.devops.apitest.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 接口状态枚举
 *
 * @author hfwas
 */
@Getter
@RequiredArgsConstructor
public enum ApiStatusEnum {

    /**
     * 草稿：新建或编辑中，不可用于调试
     */
    DRAFT("DRAFT", "草稿"),

    /**
     * 已发布：可用状态，可被引用和调试
     */
    PUBLISHED("PUBLISHED", "已发布"),

    /**
     * 已废弃：不可用，仅保留历史记录
     */
    DEPRECATED("DEPRECATED", "已废弃");

    @EnumValue
    private final String code;
    private final String label;
}