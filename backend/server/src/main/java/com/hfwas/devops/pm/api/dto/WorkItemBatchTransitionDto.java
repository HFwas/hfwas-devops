package com.hfwas.devops.pm.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkItemBatchTransitionDto {
    private List<Long> ids;
    private String toStatus;
    private String transitionId;
    private Map<String, Object> fields;
    private Map<String, Long> versions;
    private boolean dryRun;
    private String reason;
}
