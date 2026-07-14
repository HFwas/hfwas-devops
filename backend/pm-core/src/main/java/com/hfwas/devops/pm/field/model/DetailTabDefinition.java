package com.hfwas.devops.pm.field.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailTabDefinition {
    private String id;
    private String name;
    private boolean implemented;
    private boolean defaultEnabled;
    private int sortOrder;
}
