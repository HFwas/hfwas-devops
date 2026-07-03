package com.hfwas.devops.pm.query.model;

import lombok.Data;

@Data
public class SortSpec {
    private String field;
    private String order = "DESC";
}
