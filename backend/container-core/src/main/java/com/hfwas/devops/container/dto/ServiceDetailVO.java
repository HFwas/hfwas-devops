package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ServiceDetailVO extends ServiceSummaryVO {
    private String uid;
    private Map<String, String> selector;
    private String sessionAffinity;
    private String yaml;
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private List<ServicePortVO> ports;

    @Data
    public static class ServicePortVO {
        private String name;
        private int port;
        private String targetPort;
        private String nodePort;
        private String protocol;
    }
}