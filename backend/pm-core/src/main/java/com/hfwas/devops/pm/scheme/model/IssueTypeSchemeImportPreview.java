package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IssueTypeSchemeImportPreview {
    private String typeCode;
    private String sourceTypeCode;
    private List<String> sections = new ArrayList<>();
    private int customFieldCount;
    private int layoutFieldCount;
    private int fieldsToCreate;
    private int fieldsToUpdate;
    private int statusCount;
    private boolean statusWorkflowWillApply;
    private List<String> warnings = new ArrayList<>();
}
