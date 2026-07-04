package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class AllowedTransitionsQueryDto {
    private Long projectId;
    private String typeCode;
    private String fromStatus;
}
