package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.file.Node;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/24
 */
@Data
public class Configurations {
    @JsonProperty("CVE_data_version")
    private String cveDataVersion;
    @JsonProperty("nodes")
    private List<Node> nodes;
}
