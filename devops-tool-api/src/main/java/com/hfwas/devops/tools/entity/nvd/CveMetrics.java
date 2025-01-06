package com.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class CveMetrics {
    private List<CveCvssMetricV2> cvssMetricV2s;
}
