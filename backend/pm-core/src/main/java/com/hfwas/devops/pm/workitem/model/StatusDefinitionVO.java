package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.List;

@Data
public class StatusDefinitionVO {
    private Long id;
    private String statusCode;
    private String statusName;
    private Integer sortOrder;
    private Integer isInitial;
    private Integer isFinal;
    /** 从本状态出发的流转（含 id / name / toStatus / validators / postFunctions） */
    private List<TransitionVO> transitions;
}
