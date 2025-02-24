package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CveDescription {
    @JsonProperty("lang")
    private String lang;
    @JsonProperty("value")
    private String value;
}
