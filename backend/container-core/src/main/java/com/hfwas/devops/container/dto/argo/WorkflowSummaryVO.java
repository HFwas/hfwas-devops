package com.hfwas.devops.container.dto.argo;

import lombok.Data;

import java.util.Map;

/** 工作流摘要（列表用） */
@Data
public class WorkflowSummaryVO {
    private String name;
    private String namespace;
    private String status;       // Running / Succeeded / Failed / Error / Pending
    private String startedAt;
    private String finishedAt;
    private String duration;
    private String trigger;      // manual / cron / webhook / event
    private String entrypoint;
    private String createdBy;
}