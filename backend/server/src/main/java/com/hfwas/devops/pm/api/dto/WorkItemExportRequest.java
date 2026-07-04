package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.query.model.QuerySpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkItemExportRequest {
    private Long projectId;
    private String typeCode;
    /** Export selected item ids (string to preserve snowflake precision). */
    private List<String> ids = new ArrayList<>();
    private QuerySpec querySpec;
    private List<String> fieldKeys = new ArrayList<>();
}
