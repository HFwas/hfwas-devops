package com.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class NvdResponse {
    private Integer resultsPerPage;
    private Integer startIndex;
    private Integer totalResults;
    private Integer format;
    private Integer version;
    private Integer timestamp;
    private List<CveChanges> cveChanges;
    public List<Vulnerabilities> vulnerabilities;
}
