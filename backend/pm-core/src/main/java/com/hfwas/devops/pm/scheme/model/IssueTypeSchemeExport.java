package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Unified export bundle for one issue type configuration (Jira-like issue type scheme).
 * Combines field scheme, status workflow, and future extensible sections.
 */
@Data
public class IssueTypeSchemeExport {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "pm_issue_type_scheme";

    private int schemaVersion = SCHEMA_VERSION;
    private String kind = KIND;
    private String typeCode;
    private String typeName;
    private FieldSchemeSection fieldScheme;
    private StatusWorkflowSection statusWorkflow;
    private LocalDateTime exportedAt;
}
