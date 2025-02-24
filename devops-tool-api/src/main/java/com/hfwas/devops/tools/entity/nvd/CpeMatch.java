package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/14
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CpeMatch {
    @JsonProperty("vulnerable")
    private Boolean vulnerable;
    @JsonProperty("cpe23Uri")
    private String cpe23Uri;
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
    @JsonProperty("cpe_name")
    private List<JsonNode> cpeName;
}
