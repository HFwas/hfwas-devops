package cn.hfwas.devops.tools.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CvssV2SeverityEnums {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    ;

    private String value;
}
