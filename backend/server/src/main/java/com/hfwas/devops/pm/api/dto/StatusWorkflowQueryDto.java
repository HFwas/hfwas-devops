package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class StatusWorkflowQueryDto {
    private Long projectId;
    private String typeCode;
}
