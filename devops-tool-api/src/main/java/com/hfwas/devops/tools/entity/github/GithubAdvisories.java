package com.hfwas.devops.tools.entity.github;

import com.google.gson.annotations.SerializedName;
import com.hfwas.devops.tools.entity.cwe.CvssSeverity;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.entity.github
 * @date 2025/1/8
 */
@Data
public class GithubAdvisories {
    @SerializedName("id")
    private String ghsaId;
    @SerializedName("schema_version")
    private String schemaVersion;
    @SerializedName("modified")
    private String modified;
    @SerializedName("published")
    private String published;
    @SerializedName("aliases")
    private List<String> aliases;
    @SerializedName("details")
    private String details;
    @SerializedName("severity")
    private List<CvssSeverity> severity;
    @SerializedName("affected")
    private List<GithubAffected> affected;
    @SerializedName("references")
    private List<GithubReference> references;
    @SerializedName("database_specific")
    private DatabaseSpecific databaseSpecific;
}
