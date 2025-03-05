package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.enums.EcosystemEnums;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class GithubApiPackage {
    @JsonProperty("ecosystem")
    private EcosystemEnums ecosystem;

    @JsonProperty("name")
    private String name;
}
