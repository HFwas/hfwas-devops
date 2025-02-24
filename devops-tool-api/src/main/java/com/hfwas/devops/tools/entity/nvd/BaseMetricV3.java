package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.file.CvssV3;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/24
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseMetricV3 {
    @JsonProperty("cvssV3")
    private CvssV3 cvssV3;
    @JsonProperty("exploitabilityScore")
    private Double exploitabilityScore;
    @JsonProperty("impactScore")
    private Double impactScore;
}
