package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IssueTypeSchemeImportResult {
    private String typeCode;
    private int fieldsCreated;
    private int fieldsUpdated;
    private int fieldsSkipped;
    private boolean layoutApplied;
    private boolean statusWorkflowApplied;
    private int statusCount;
    private List<String> sectionsApplied = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
