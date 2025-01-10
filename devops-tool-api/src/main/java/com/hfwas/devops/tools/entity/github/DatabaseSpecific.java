package com.hfwas.devops.tools.entity.github;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class DatabaseSpecific {
    @SerializedName("cwe_ids")
    private List<String> cweIds;
    @SerializedName("severity")
    private String severity;
    @SerializedName("github_reviewed")
    private Boolean githubReviewed;
    @SerializedName("github_reviewed_at")
    private String githubReviewedAt;
    @SerializedName("nvd_published_at")
    private String nvdPublishedAt;
}
