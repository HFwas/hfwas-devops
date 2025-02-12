package com.hfwas.devops.codeScan.pmd;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.codeScan.pmd
 * @date 2025/2/12
 */
@Data
public class PmdCsv {
    @SerializedName(value = "Problem")
    private Integer problem;
    @SerializedName(value = "Package")
    private String packages;
    @SerializedName(value = "File")
    private String file;
    @SerializedName(value = "Priority")
    private Integer priority;
    @SerializedName(value = "Line")
    private Integer line;
    @SerializedName(value = "Description")
    private String description;
    @SerializedName(value = "Rule set")
    private String ruleSet;
    @SerializedName(value = "Rule")
    private String rule;
}
