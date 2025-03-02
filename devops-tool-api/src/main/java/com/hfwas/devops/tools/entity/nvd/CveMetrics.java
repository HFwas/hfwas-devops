package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CveMetrics {
    @JsonProperty("cvssMetricV2")
    @JsonPropertyDescription("CVSS V2.0 score.")
    private List<CveCvssMetricV2> cvssMetricV2;
    @JsonProperty("cvssMetricV30")
    @JsonPropertyDescription("CVSS V3.0 score.")
    private List<CveCvssMetricV30> cvssMetricV30;
    @JsonProperty("cvssMetricV31")
    @JsonPropertyDescription("CVSS V3.1 score.")
    private List<CveCvssMetricV31> cvssMetricV31;
    @JsonProperty("cvssMetricV40")
    @JsonPropertyDescription("CVSS V4.0 score.")
    private List<CveCvssMetricV40> cvssMetricV40;
}
