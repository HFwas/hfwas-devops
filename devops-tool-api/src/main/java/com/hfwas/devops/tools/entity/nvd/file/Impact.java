package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.BaseMetricV2;
import com.hfwas.devops.tools.entity.nvd.BaseMetricV3;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Impact {
    @JsonProperty("baseMetricV3")
    private BaseMetricV3 baseMetricV3;
    @JsonProperty("baseMetricV2")
    private BaseMetricV2 baseMetricV2;
}
