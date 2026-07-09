package com.hfwas.devops.pm.workitem.model;

import java.util.Set;

public final class TransitionPostFunctionType {

    /** 自动设置字段值（系统字段或自定义字段） */
    public static final String SET_FIELD = "SET_FIELD";
    /** 通知当前负责人 */
    public static final String NOTIFY_ASSIGNEE = "NOTIFY_ASSIGNEE";
    /** 通知指定用户 */
    public static final String NOTIFY_USER = "NOTIFY_USER";
    /** 发送钉钉/飞书 Webhook（走租户通知渠道配置） */
    public static final String WEBHOOK = "WEBHOOK";

    private static final Set<String> ALL = Set.of(SET_FIELD, NOTIFY_ASSIGNEE, NOTIFY_USER, WEBHOOK);

    private TransitionPostFunctionType() {
    }

    public static boolean isKnown(String type) {
        return type != null && ALL.contains(type);
    }
}
