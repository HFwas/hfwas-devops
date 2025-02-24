package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CveMetrics {
    @JsonProperty("cvssMetricV2")
    @JsonPropertyDescription("CVSS V2.0 score.")
    private List<JsonObject> cvssMetricV2;
}
