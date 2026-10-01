package com.hfwas.devops.container.dto.argo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/** 工作流详情 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowDetailVO extends WorkflowSummaryVO {
    private String phase;
    private List<Map<String, Object>> parameters;
    /** WorkflowTemplate 定义（JSON 结构） */
    private List<JsonNode> templates;
    /** 节点运行时状态（key=nodeId） */
    private Map<String, JsonNode> nodeStatus;
}