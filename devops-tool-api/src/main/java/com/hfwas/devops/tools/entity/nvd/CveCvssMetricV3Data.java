package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/3/2
 */
@Data
public class CveCvssMetricV3Data {
    @JsonProperty("version")
    private String version;
    @JsonProperty("vectorString")
    private String vectorString;
    @JsonProperty("baseScore")
    private Double baseScore;
    @JsonProperty("baseSeverity")
    private String baseSeverity;
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
}
