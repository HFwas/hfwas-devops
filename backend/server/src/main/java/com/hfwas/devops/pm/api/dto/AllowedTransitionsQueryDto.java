package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class AllowedTransitionsQueryDto {
    private Long projectId;
    private String typeCode;
    private String fromStatus;
    /** 有值时按事项字段评估 Condition；无值时仅返回无 Condition 的流转 */
    private Long workItemId;
}
