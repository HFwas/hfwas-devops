package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.CpeMatch;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
public class Node {
    @JsonProperty("operator")
    private String operator;
    @JsonProperty("negate")
    private Boolean negate;
    @JsonProperty("children")
    private List<Node> children;
    @JsonProperty("cpe_match")
    private List<CpeMatch> cpeMatch;
}
