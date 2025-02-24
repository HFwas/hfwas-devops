package com.hfwas.devops.tools.entity.nvd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd
 * @date 2025/2/22
 */
@Data
public class NvdMeta {
    @JsonProperty("lastModifiedDate")
    private String lastModifiedDate;
    @JsonProperty("size")
    private Integer size;
    @JsonProperty("zipSize")
    private Integer zipSize;
    @JsonProperty("gzSize")
    private Integer gzSize;
    @JsonProperty("sha256")
    private String sha256;
}
