package com.hfwas.devops.tools.entity.nvd;

import lombok.Data;

import java.util.List;

@Data
public class NvdResponse {
    private String resultsPerPage;
    private String startIndex;
    private String totalResults;
    private String format;
    private String version;
    private String timestamp;
//    private List<CveChanges> cveChanges;
    public List<Vulnerabilities> vulnerabilities;
}
