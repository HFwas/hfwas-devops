package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CvssV3 {
    @JsonProperty("version")
    private String version;

    @JsonProperty("vectorString")
    private String vectorString;

    @JsonProperty("attackVector")
    private String attackVector;

    @JsonProperty("attackComplexity")
    private String attackComplexity;

    @JsonProperty("privilegesRequired")
    private String privilegesRequired;

    @JsonProperty("userInteraction")
    private String userInteraction;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("confidentialityImpact")
    private String confidentialityImpact;

    @JsonProperty("integrityImpact")
    private String integrityImpact;

    @JsonProperty("availabilityImpact")
    private String availabilityImpact;

    @JsonProperty("baseScore")
    private Double baseScore;

    @JsonProperty("baseSeverity")
    private String baseSeverity;
}
