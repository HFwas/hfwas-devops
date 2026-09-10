package com.hfwas.devops.container.dto;

import lombok.Data;

@Data
public class ArtifactVO {
    private String digest;
    private String size;
    private java.util.List<TagVO> tags;
    private ScanOverviewVO scanOverview;
}