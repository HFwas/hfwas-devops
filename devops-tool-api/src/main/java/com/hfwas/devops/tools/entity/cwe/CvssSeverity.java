package com.hfwas.devops.tools.entity.cwe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CvssSeverity {
    @SerializedName("type")
    private String type;
    @SerializedName("score")
    private String score;
}
