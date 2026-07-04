package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FieldSchemeImportPreview {
    private String typeCode;
    private String sourceTypeCode;
    private int customFieldCount;
    private int layoutFieldCount;
    private int fieldsToCreate;
    private int fieldsToUpdate;
    private List<String> warnings = new ArrayList<>();
}
