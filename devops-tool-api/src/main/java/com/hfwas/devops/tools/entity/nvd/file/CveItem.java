package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.entity.nvd.Configurations;
import com.hfwas.devops.tools.entity.nvd.Cve;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CveItem {
    @JsonProperty("cve")
    private Cve cve;
    @JsonProperty("configurations")
    private Configurations configurations;
    @JsonProperty("impact")
    private Impact impact;
    @JsonProperty("publishedDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm'Z'")
    private LocalDateTime publishedDate;
    @JsonProperty("lastModifiedDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm'Z'")
    private LocalDateTime lastModifiedDate;
}
