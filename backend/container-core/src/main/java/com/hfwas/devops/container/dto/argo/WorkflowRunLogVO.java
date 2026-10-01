package com.hfwas.devops.container.dto.argo;

import lombok.Data;

/** 工作流节点日志 */
@Data
public class WorkflowRunLogVO {
    private String nodeId;
    private String nodeName;
    private String logs;
    private String phase;
}