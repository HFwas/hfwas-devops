package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FieldSchemeImportResult {
    private int fieldsCreated;
    private int fieldsUpdated;
    private int fieldsSkipped;
    private boolean layoutApplied;
    private List<String> warnings = new ArrayList<>();
}
