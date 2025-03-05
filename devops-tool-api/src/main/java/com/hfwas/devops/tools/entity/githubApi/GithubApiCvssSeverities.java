package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class GithubApiCvssSeverities {
    @JsonProperty("cvss_v3")
    private GihubApiCvssV3 cvssV3;

    @JsonProperty("cvss_v4")
    private GihubApiCvssV4 cvssV4;
}
