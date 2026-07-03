package com.hfwas.devops.pm.field.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TypeFieldLayoutConfig {
    private List<String> listFields = new ArrayList<>();
    private List<String> searchFields = new ArrayList<>();
    private List<String> createFields = new ArrayList<>();
}
