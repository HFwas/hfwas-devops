package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/3/2
 */
@Data
public class CveCvssMetricV30 {
    @JsonProperty("source")
    private String source;
    @JsonProperty("type")
    private String type;
    @JsonProperty("cvssData")
    private CveCvssMetricV3Data cvssData;
    @JsonProperty("exploitabilityScore")
    private Integer exploitabilityScore;
    @JsonProperty("impactScore")
    private Integer impactScore;
}
