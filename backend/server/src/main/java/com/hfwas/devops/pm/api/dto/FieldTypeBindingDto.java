package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class FieldTypeBindingDto {
    private Long projectId;
    private Long fieldId;
    private String typeCode;
}
