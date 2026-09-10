package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClusterComponentVO {
    private String kubernetesVersion;
    private int nodeCount;
    private List<NodeComponentVO> nodes;
    private List<SystemComponentVO> systemComponents;

    @Data
    public static class NodeComponentVO {
        private String name;
        private String kubeletVersion;
        private String containerRuntime;
        private String osImage;
        private String kernelVersion;
        private String architecture;
        private String status;
    }

    @Data
    public static class SystemComponentVO {
        private String name;
        private String namespace;
        private String status;
        private String version;
        private int readyReplicas;
        private int desiredReplicas;
    }
}