package com.hfwas.devops.container.dto.argo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

/** 工作流运行记录 */
@Data
public class WorkflowRunVO {
    private String id;
    private String workflowName;
    private String status;
    private String startedAt;
    private String finishedAt;
    private String duration;
    private String trigger;
    private String triggeredBy;
    private Map<String, JsonNode> nodeStatus;
}