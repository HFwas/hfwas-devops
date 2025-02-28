package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.file.CveDataMeta;
import com.hfwas.devops.tools.entity.nvd.file.Description;
import com.hfwas.devops.tools.entity.nvd.file.ReferenceData;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cve {
    @JsonProperty("id")
    private String id;
    @JsonProperty("data_type")
    private String dataType;
    @JsonProperty("data_format")
    private String dataFormat;
    @JsonProperty("data_version")
    private String dataVersion;
    @JsonProperty("CVE_data_meta")
    private CveDataMeta CVE_data_meta;
    @JsonProperty("description")
    private Description description;

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
    private List<Configurations> configurations;
    @JsonProperty("references")
    private List<ReferenceData> references;
    @JsonProperty("evaluatorSolution")
    private String evaluatorSolution;
    @JsonProperty("evaluatorImpact")
    private String evaluatorImpact;
    @JsonProperty("evaluatorComment")
    private String evaluatorComment;
}
