package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkloadEnvUpdateDTO {
    private List<ContainerEnvUpdate> containers = new ArrayList<>();

    @Data
    public static class ContainerEnvUpdate {
        private String name;
        private boolean init;
        private List<WorkloadEnvVO.EnvItemVO> env = new ArrayList<>();
    }
}
