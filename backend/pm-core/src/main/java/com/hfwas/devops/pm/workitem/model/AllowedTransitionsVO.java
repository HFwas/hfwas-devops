package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.List;

@Data
public class AllowedTransitionsVO {
    private String fromStatus;
    private List<StatusDefinitionVO> targets;
}
