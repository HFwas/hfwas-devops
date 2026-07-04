package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import lombok.Data;

@Data
public class IssueTypeSchemePreviewDto {
    private Long projectId;
    private String typeCode;
    private IssueTypeSchemeExport scheme;
    /** Legacy field-only scheme for backward compatibility */
    private TypeFieldSchemeExport legacyScheme;
}
