package com.hfwas.devops.container.dto.argo;

import lombok.Data;

/** K8s Event 对象 */
@Data
public class WorkflowEventVO {
    private String id;
    private String type;
    private String reason;
    private String message;
    private String timestamp;
    private String source;
}