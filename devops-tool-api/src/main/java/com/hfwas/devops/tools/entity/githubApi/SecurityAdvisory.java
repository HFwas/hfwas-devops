package com.hfwas.devops.tools.entity.githubApi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hfwas.devops.tools.enums.SecurityTypeEnums;
import com.hfwas.devops.tools.enums.SeverityEnums;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.githubApi
 * @date 2025/3/5
 */
@Data
public class SecurityAdvisory {
    @JsonProperty("ghsa_id")
    private String ghsaId;

    @JsonProperty("cve_id")
    private String cveId;

    @JsonProperty("url")
    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("description")
    private String description;

    @JsonProperty("type")
    private SecurityTypeEnums type;

    @JsonProperty("severity")
    private SeverityEnums severity;

    @JsonProperty("repository_advisory_url")
    private String repositoryAdvisoryUrl;

    @JsonProperty("source_code_location")
    private String sourceCodeLocation;

    @JsonProperty("identifiers")
    private List<SecurityIdentifier> identifiers;

    @JsonProperty("references")
    private List<String> references;

    @JsonProperty("published_at")
    private String publishedAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("github_reviewed_at")
    private String githubReviewedAt;

    @JsonProperty("nvd_published_at")
    private String nvdPublishedAt;

    @JsonProperty("withdrawn_at")
    private String withdrawnAt;

    @JsonProperty("vulnerabilities")
    private List<GithubApiVulnerability> vulnerabilities;

    @JsonProperty("cvss_severities")
    private GithubApiCvssSeverities cvssSeverities;

    @JsonProperty("cwes")
    private List<GithubApiCwe> cwes;

    @JsonProperty("credits")
    private List<Object> credits;

    @JsonProperty("cvss")
    private GihubApiCvssV3 cvss;
}
