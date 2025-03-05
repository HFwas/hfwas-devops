package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class GithubApiCwe {
    @JsonProperty("cwe_id")
    private String cweId;

    @JsonProperty("name")
    private String name;
}
