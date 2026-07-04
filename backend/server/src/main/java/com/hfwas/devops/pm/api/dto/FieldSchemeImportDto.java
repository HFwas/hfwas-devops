package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.field.model.ProjectFieldSchemeExport;
import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import lombok.Data;

@Data
public class FieldSchemeImportDto {
    private Long projectId;
    private String typeCode;
    private FieldSchemeImportMode mode = FieldSchemeImportMode.MERGE;
    /** Single type scheme import */
    private TypeFieldSchemeExport scheme;
    /** Project-wide import */
    private ProjectFieldSchemeExport projectScheme;
}
