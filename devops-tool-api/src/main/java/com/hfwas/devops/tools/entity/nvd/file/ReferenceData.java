package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceData {
    @JsonProperty("url")
    private String url;
    @JsonProperty("name")
    private String name;
    @JsonProperty("refsource")
    private String refsource;
    @JsonProperty("tags")
    private List<String> tags;
}
