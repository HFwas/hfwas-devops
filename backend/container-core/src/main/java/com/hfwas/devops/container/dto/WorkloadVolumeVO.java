package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkloadVolumeVO {
    private List<ContainerRef> containers = new ArrayList<>();
    private List<VolumeItem> volumes = new ArrayList<>();
    private List<PvcOption> unboundPvcs = new ArrayList<>();

    @Data
    public static class ContainerRef {
        private String name;
        private boolean init;
    }

    @Data
    public static class VolumeItem {
        private String name;
        /** pvc、configMap、secret、emptyDir、hostPath、claimTemplate、other */
        private String type;
        private String source;
        private boolean readOnly;
        private List<MountItem> mounts = new ArrayList<>();
    }

    @Data
    public static class MountItem {
        private String container;
        private boolean init;
        private String mountPath;
        private String subPath;
        private boolean readOnly;
    }

    @Data
    public static class PvcOption {
        private String name;
        private String status;
        private String storageClass;
        private String capacity;
    }
}
