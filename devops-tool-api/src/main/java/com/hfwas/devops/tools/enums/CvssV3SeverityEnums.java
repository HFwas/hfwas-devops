package com.hfwas.devops.tools.enums;

import lombok.Getter;

@Getter
public enum CvssV3SeverityEnums {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL"),
    ;

    private String value;

    CvssV3SeverityEnums(String value) {
        this.value = value;
    }
}
