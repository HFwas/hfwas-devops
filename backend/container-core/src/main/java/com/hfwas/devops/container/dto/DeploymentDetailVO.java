package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class DeploymentDetailVO extends DeploymentSummaryVO {
    private String uid;
    private String image;
    private String yaml;
    private String selector;
    private String status;
}