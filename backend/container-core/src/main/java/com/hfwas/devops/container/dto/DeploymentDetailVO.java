package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DeploymentDetailVO extends DeploymentSummaryVO {
    private String uid;
    private String image;
    private String yaml;
    private String selector;
    private String status;
    private List<ContainerResourceVO> containers;
    private List<VolumeMountVO> volumes;
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private String revisionHistoryLimit;
    private String minReadySeconds;

    @Data
    public static class ContainerResourceVO {
        private String name;
        private String image;
        private String cpuRequest;
        private String cpuLimit;
        private String memRequest;
        private String memLimit;
        private List<VolumeMountVO> volumeMounts;
        private List<PortVO> ports;
        private String command;
        private String args;
    }

    @Data
    public static class VolumeMountVO {
        private String name;
        private String mountPath;
        private String readOnly;
        private String subPath;
        private String volumeType;
    }

    @Data
    public static class PortVO {
        private String name;
        private int containerPort;
        private String protocol;
    }
}