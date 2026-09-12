package com.hfwas.devops.pipeline.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchDependencyScanSubmitDTO {
    private List<DependencyScanSubmitDTO> scans;
}