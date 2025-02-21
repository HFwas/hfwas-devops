package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/14
 */
@Data
public class CpeMatch {
    @JsonProperty("vulnerable")
    private Boolean vulnerable;
    @JsonProperty("criteria")
    private String criteria;
    @JsonProperty("matchCriteriaId")
    private String matchCriteriaId;
    @JsonProperty("versionStartExcluding")
    private String versionStartExcluding;
    @JsonProperty("versionStartIncluding")
    private String versionStartIncluding;
    @JsonProperty("versionEndExcluding")
    private String versionEndExcluding;
    @JsonProperty("versionEndIncluding")
    private String versionEndIncluding;
}
