package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

@Data
public class TransitionPostFunctionVO {
    /** @see TransitionPostFunctionType */
    private String type;
    /** SET_FIELD: 字段 key（如 priority、assignee_id 或自定义 fieldKey） */
    private String fieldKey;
    /** SET_FIELD: 目标值（字符串/数字/布尔，由字段类型解析） */
    private Object value;
    /** NOTIFY_USER: 目标用户 ID */
    private Long userId;
    /** 通知/Webhook 标题，支持 {title} {itemKey} {fromStatus} {toStatus} 占位符 */
    private String title;
    /** 通知/Webhook 正文，支持同上占位符 */
    private String content;
}
