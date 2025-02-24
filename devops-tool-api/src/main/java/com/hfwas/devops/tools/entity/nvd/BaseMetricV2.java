package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.file.CvssV2;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/24
 */
@Data
public class BaseMetricV2 {
    @JsonProperty("cvssV2")
    private CvssV2 cvssV2;
    @JsonProperty("severity")
    private String severity;
    @JsonProperty("exploitabilityScore")
    private Double exploitabilityScore;
    @JsonProperty("impactScore")
    private Double impactScore;
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
