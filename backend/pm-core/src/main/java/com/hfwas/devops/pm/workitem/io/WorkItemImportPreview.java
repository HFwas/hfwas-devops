package com.hfwas.devops.pm.workitem.io;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class WorkItemImportPreview {
    private int totalRows;
    private int validRows;
    private List<String> detectedHeaders = new ArrayList<>();
    private List<Map<String, String>> sampleRows = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
