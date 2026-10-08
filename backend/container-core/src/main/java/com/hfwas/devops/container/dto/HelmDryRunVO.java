package com.hfwas.devops.container.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HelmDryRunVO {
    private String manifest;
    private List<HelmReleaseResourceVO> resources = new ArrayList<>();
}
