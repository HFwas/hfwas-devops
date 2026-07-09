package com.hfwas.devops.pm.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkItemTransitionDto {
    private String transitionId;
    /** 流转确认时一并提交的字段值（系统字段或自定义字段） */
    private Map<String, Object> fields;
}
