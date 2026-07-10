package com.hfwas.devops.pm.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectIssueTypesSaveDto {
    private Long projectId;
    private List<String> typeCodes;
}
