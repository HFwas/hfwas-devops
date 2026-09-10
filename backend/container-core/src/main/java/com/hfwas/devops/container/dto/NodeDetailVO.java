package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NodeDetailVO {
    private String name;
    private String status;
    private String role;
    private String kubeletVersion;
    private String containerRuntime;
    private String osImage;
    private String kernelVersion;
    private String architecture;
    private String podCIDR;
    private String providerID;
    private int podCount;
    private long memoryCapacity;
    private int cpuCapacity;
    private String age;
    private String creationTimestamp;

    // Detail-only fields
    private String uid;
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private List<NodeAddressVO> addresses;
    private List<NodeTaintVO> taints;
    private NodeSystemInfoVO nodeInfo;
    private Map<String, String> capacity;
    private Map<String, String> allocatable;
    private List<NodeImageVO> images;

    @Data
    public static class NodeAddressVO {
        private String type;
        private String address;
    }

    @Data
    public static class NodeTaintVO {
        private String key;
        private String value;
        private String effect;
    }

    @Data
    public static class NodeSystemInfoVO {
        private String machineID;
        private String systemUUID;
        private String bootID;
        private String kernelVersion;
        private String osImage;
        private String containerRuntimeVersion;
        private String kubeletVersion;
        private String kubeProxyVersion;
        private String operatingSystem;
        private String architecture;
    }

    @Data
    public static class NodeImageVO {
        private String name;
        private long sizeBytes;
    }
}