package com.hfwas.devops.pm.workitem.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransitionMetaVO {
    private String transitionId;
    private String fromStatus;
    private String toStatus;
    private String name;
    private List<TransitionValidatorVO> validators = new ArrayList<>();
    private List<TransitionFieldMetaVO> requiredFields = new ArrayList<>();
}
