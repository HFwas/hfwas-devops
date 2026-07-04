package com.hfwas.devops.pm.workitem.io;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkItemImportResult {
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
