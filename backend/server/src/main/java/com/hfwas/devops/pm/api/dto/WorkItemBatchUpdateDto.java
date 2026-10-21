package com.hfwas.devops.pm.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkItemBatchUpdateDto {
    private List<Long> ids;
    private Map<String, Object> patch;
    private Map<String, Long> versions;
    private boolean dryRun;
}
