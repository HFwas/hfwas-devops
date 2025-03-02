package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CveCvssMetricV2Data {
    @JsonProperty("version")
    private String version;
    @JsonProperty("vectorString")
    private String vectorString;
    @JsonProperty("baseScore")
    private Integer baseScore;
    @JsonProperty("accessVector")
    private String accessVector;
    @JsonProperty("accessComplexity")
    private String accessComplexity;
    @JsonProperty("authentication")
    private String authentication;
    @JsonProperty("confidentialityImpact")
    private String confidentialityImpact;
    @JsonProperty("integrityImpact")
    private String integrityImpact;
    @JsonProperty("availabilityImpact")
    private String availabilityImpact;
}
