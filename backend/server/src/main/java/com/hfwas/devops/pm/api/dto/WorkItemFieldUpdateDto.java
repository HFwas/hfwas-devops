package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class WorkItemFieldUpdateDto {
    private String field;
    private Object value;
    private Long version;
    private String transitionId;
}
