package com.hfwas.devops.pm.scheme.model;

import lombok.Data;

import java.util.List;

@Data
public class ExportedStatusDefinition {
    private String statusCode;
    private String statusName;
    private Integer sortOrder;
    private Integer isInitial;
    private Integer isFinal;
    private List<String> transitions;
}
