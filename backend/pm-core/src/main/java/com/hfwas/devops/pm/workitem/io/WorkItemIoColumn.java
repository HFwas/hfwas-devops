package com.hfwas.devops.pm.workitem.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WorkItemIoColumn {
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    @JsonProperty("systemField")
    private boolean systemField;
    /** Can be included in export. */
    @JsonProperty("exportable")
    private boolean exportable = true;
    /** Can be mapped on import (itemKey is match-only). */
    @JsonProperty("importable")
    private boolean importable = true;
    @JsonProperty("defaultSelected")
    private boolean defaultSelected = true;
}
