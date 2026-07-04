package com.hfwas.devops.pm.workitem.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkItemFieldChange {
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private Object oldValue;
    private Object newValue;
}
