package com.hfwas.devops.tools.entity.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class GithubAffected {
    @SerializedName("package")
    private JsonObject packages;
    @SerializedName("ranges")
    private List<Range> ranges;
}
