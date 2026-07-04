package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import lombok.Data;

@Data
public class FieldSchemePreviewDto {
    private Long projectId;
    private String typeCode;
    private TypeFieldSchemeExport scheme;
}
