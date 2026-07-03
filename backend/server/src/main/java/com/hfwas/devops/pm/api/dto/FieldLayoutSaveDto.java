package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.TypeFieldLayoutConfig;
import lombok.Data;

@Data
public class FieldLayoutSaveDto {
    private Long projectId;
    private String typeCode;
    private TypeFieldLayoutConfig layout;
}
