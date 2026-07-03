package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class BoardQueryDto {
    private Long projectId;
    private String typeCode;
}
