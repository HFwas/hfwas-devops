package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class GihubApiCvssV4 {
    @JsonProperty("vector_string")
    private String vectorString;

    @JsonProperty("score")
    private double score;
}
