package com.hfwas.devops.tools.entity.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.github
 * @date 2025/1/8
 */
@Data
public class GithubAdvisories {
    private String ghsa_id;
    private String cve_id;
    private String url;
    private String html_url;
    private String summary;
    private String description;
    private String type;
    private String severity;
    private String repository_advisory_url;
    private String source_code_location;
    private JsonArray identifiers;
    private JsonArray references;
    private String published_at;
    private String updated_at;
    private String github_reviewed_at;
    private String nvd_published_at;
    private String withdrawn_at;
    private JsonArray vulnerabilities;
    private JsonObject cvss;
    private JsonArray cwes;
    private JsonArray credits;
    private JsonObject cvss_severities;
}
