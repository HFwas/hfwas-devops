package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceDetailVO extends ServiceSummaryVO {
    private String uid;
    private Map<String, String> selector;
    private String sessionAffinity;
    private String yaml;
}