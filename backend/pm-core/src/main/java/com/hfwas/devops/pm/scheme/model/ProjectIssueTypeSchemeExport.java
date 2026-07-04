package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Export bundle for all issue type schemes in a project. */
@Data
public class ProjectIssueTypeSchemeExport {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "pm_project_issue_type_schemes";

    private int schemaVersion = SCHEMA_VERSION;
    private String kind = KIND;
    private List<IssueTypeSchemeExport> schemes = new ArrayList<>();
    private LocalDateTime exportedAt;
}
