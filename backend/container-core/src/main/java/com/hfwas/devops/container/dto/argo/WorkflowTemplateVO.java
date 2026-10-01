package com.hfwas.devops.container.dto.argo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** WorkflowTemplate 摘要 */
@Data
public class WorkflowTemplateVO {
    private String name;
    private String namespace;
    private String description;
    private String entrypoint;
    private List<JsonNode> templates;
    private List<Map<String, Object>> arguments;
    private String createdAt;
}