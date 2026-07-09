package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class TransitionMetaQueryDto {
    private Long projectId;
    private String typeCode;
    /** 可选：用于校验流转是否属于当前状态 */
    private String fromStatus;
    private String transitionId;
}
