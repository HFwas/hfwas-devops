package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class NvdResponse {
    @JsonProperty("resultsPerPage")
    private Integer resultsPerPage;
    @JsonProperty("startIndex")
    private Integer startIndex;
    @JsonProperty("totalResults")
    private Integer totalResults;
    @JsonProperty("format")
    private String format;
    @JsonProperty("version")
    private String version;
    @JsonProperty("timestamp")
    private String timestamp;
    // @JsonProperty("cveChanges")
    // private List<CveChanges> cveChanges;
    @JsonProperty("vulnerabilities")
    public List<Vulnerabilities> vulnerabilities;
}
