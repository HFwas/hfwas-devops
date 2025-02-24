package com.hfwas.devops.tools.entity.nvd.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.nvd.file
 * @date 2025/2/24
 */
@Data
public class NvdCveFeed {
    @JsonProperty("CVE_data_type")
    private String cveDataType;
    @JsonProperty("CVE_data_format")
    private String cveDataFormat;
    @JsonProperty("CVE_data_version")
    private String cveDataVersion;
    @JsonProperty("CVE_data_numberOfCVEs")
    private String cveDataNumberOfCVEs;
    @JsonProperty("CVE_data_timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm'Z'")
    private LocalDateTime cveDataTimestamp;
    @JsonProperty("CVE_Items")
    private List<CveItem> cveItems;
}
