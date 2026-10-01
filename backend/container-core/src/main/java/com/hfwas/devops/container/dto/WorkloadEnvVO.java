package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkloadEnvVO {
    private List<ContainerEnvVO> containers = new ArrayList<>();

    @Data
    public static class ContainerEnvVO {
        private String name;
        private boolean init;
        private List<EnvItemVO> env = new ArrayList<>();
        private List<ImportedEnvVO> imported = new ArrayList<>();
    }

    @Data
    public static class EnvItemVO {
        private String name;
        private String value;
        /** secret、configMap、field、resource；空表示字面量 */
        private String sourceType;
        private String sourceName;
        private String sourceKey;
    }

    @Data
    public static class ImportedEnvVO {
        private String name;
        private String value;
        private String origin;
        private boolean secret;
    }
}
