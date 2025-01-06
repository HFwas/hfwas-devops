package com.hfwas.devops.tools.entity.nvd;

import lombok.Data;

@Data
public class CveCvssMetricV2 {
    private String source;
    private String type;
    private CvssData cvssData;
    private String baseSeverity;
    private Integer exploitabilityScore;
    private Integer impactScore;
    private Boolean acInsufInfo;
    private Boolean obtainAllPrivilege;
    private Boolean obtainUserPrivilege;
    private Boolean obtainOtherPrivilege;
    private Boolean userInteractionRequired;
}
