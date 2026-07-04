package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Export bundle for all issue type field schemes in a project. */
@Data
public class ProjectFieldSchemeExport {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "pm_project_field_schemes";

    private int schemaVersion = SCHEMA_VERSION;
    private String kind = KIND;
    private List<TypeFieldSchemeExport> schemes = new ArrayList<>();
    private LocalDateTime exportedAt;
}
