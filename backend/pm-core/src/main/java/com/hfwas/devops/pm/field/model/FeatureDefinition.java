package com.hfwas.devops.pm.field.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinition {
    private String id;
    private String name;
    private boolean implemented;
    private boolean defaultEnabled;
    private int sortOrder;
    /** 出现面，如 list_actions */
    private List<String> surfaces = new ArrayList<>();
}
