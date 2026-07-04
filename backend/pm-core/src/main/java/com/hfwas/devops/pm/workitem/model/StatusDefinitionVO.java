package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.List;

@Data
public class StatusDefinitionVO {
    private Long id;
    private String statusCode;
    private String statusName;
    private Integer sortOrder;
    private Integer isInitial;
    private Integer isFinal;
    private List<String> transitions;
}
