package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.List;

@Data
public class StatusWorkflowVO {
    private Long projectId;
    private String typeCode;
    private boolean customized;
    private List<StatusDefinitionVO> statuses;
}
