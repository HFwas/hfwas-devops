package com.hfwas.devops.pm.field.model;

import lombok.Data;

@Data
public class ExportedFieldOption {
    private String optionKey;
    private String optionLabel;
    private Integer sortOrder;
}
