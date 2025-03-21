package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.enums.IdentifierTypeEnums;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class SecurityIdentifier {
    @JsonProperty("value")
    private String value;

    @JsonProperty("type")
    private IdentifierTypeEnums type;
}
