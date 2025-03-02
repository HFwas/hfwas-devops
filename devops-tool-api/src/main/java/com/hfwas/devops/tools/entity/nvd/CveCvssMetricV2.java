package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CveCvssMetricV2 {
    @JsonProperty("source")
    private String source;
    @JsonProperty("type")
    private String type;
    @JsonProperty("cvssData")
    private CveCvssMetricV2Data cvssData;
    @JsonProperty("baseSeverity")
    private String baseSeverity;
    @JsonProperty("exploitabilityScore")
    private Integer exploitabilityScore;
    @JsonProperty("impactScore")
    private Integer impactScore;
    @JsonProperty("acInsufInfo")
    private Boolean acInsufInfo;
    @JsonProperty("obtainAllPrivilege")
    private Boolean obtainAllPrivilege;
    @JsonProperty("obtainUserPrivilege")
    private Boolean obtainUserPrivilege;
    @JsonProperty("obtainOtherPrivilege")
    private Boolean obtainOtherPrivilege;
    @JsonProperty("userInteractionRequired")
    private Boolean userInteractionRequired;
}
