package com.hfwas.devops.container.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PodDetailVO extends PodSummaryVO {
    private String uid;
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private List<ContainerVO> containers;
    private List<PodConditionVO> conditions;
    private String ownerReference;
    private String qosClass;
    private String yaml;
}