package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkloadVolumeUpdateDTO {
    private List<WorkloadVolumeVO.VolumeItem> volumes = new ArrayList<>();
}
