package com.hfwas.devops.pm.api.dto;

import lombok.Data;

@Data
public class WorkItemLinkDto {
    private Long sourceId;
    private Long targetId;
    private String linkType;
}
