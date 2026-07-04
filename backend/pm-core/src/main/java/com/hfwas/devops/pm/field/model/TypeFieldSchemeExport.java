package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Export bundle for one issue type field scheme (Jira Field Configuration-like). */
@Data
public class TypeFieldSchemeExport {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "pm_type_field_scheme";

    private int schemaVersion = SCHEMA_VERSION;
    private String kind = KIND;
    private String typeCode;
    private String typeName;
    private TypeFieldLayoutConfig layout = new TypeFieldLayoutConfig();
    private List<ExportedFieldDefinition> customFields = new ArrayList<>();
    private LocalDateTime exportedAt;
}
