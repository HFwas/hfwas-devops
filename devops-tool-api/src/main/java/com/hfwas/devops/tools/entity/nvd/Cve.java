package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.List;

@Data
public class Cve {
    @JsonProperty("id")
    private String id;
    @JsonProperty("sourceIdentifier")
    private String sourceIdentifier;
    @JsonProperty("published")
    private String published;
    @JsonProperty("lastModified")
    private String lastModified;
    @JsonProperty("vulnStatus")
    private String vulnStatus;
    @JsonProperty("cveTags")
    private List<String> cveTags;
    @JsonProperty("descriptions")
    private List<CveDescription> descriptions;
    @JsonProperty("metrics")
    private CveMetrics metrics;
    @JsonProperty("weaknesses")
    private List<Weaknesses> weaknesses;
    @JsonProperty("configurations")
    private List<JsonObject> configurations;
    @JsonProperty("references")
    private List<Reference> references;
    @JsonProperty("evaluatorSolution")
    private String evaluatorSolution;
    @JsonProperty("evaluatorImpact")
    private String evaluatorImpact;
    @JsonProperty("vendorComments")
    private List<JsonObject> vendorComments;
    @JsonProperty("evaluatorComment")
    private String evaluatorComment;
}
