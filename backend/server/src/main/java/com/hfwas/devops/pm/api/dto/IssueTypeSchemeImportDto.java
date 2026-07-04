package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.field.model.FieldSchemeImportMode;
import com.hfwas.devops.pm.field.model.ProjectFieldSchemeExport;
import com.hfwas.devops.pm.field.model.TypeFieldSchemeExport;
import com.hfwas.devops.pm.scheme.model.IssueTypeSchemeExport;
import com.hfwas.devops.pm.scheme.model.ProjectIssueTypeSchemeExport;
import lombok.Data;

@Data
public class IssueTypeSchemeImportDto {
    private Long projectId;
    private String typeCode;
    private FieldSchemeImportMode mode = FieldSchemeImportMode.MERGE;
    /** Unified single-type scheme */
    private IssueTypeSchemeExport scheme;
    /** Unified project-wide scheme */
    private ProjectIssueTypeSchemeExport projectScheme;
    /** Legacy field-only single scheme (backward compatible) */
    private TypeFieldSchemeExport legacyScheme;
    /** Legacy field-only project scheme */
    private ProjectFieldSchemeExport legacyProjectScheme;
}
