package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Portable custom field definition for import/export (no DB ids). */
@Data
public class ExportedFieldDefinition {
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private Integer requiredFlag;
    private Integer sortOrder;
    private Map<String, Object> config;
    private List<ExportedFieldOption> options = new ArrayList<>();
}
