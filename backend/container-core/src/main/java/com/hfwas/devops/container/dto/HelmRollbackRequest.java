package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class HelmRollbackRequest {
    private Integer revision;
}
