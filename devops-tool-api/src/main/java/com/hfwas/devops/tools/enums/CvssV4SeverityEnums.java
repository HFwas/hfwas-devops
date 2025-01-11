package com.hfwas.devops.tools.enums;

import lombok.Getter;

@Getter
public enum CvssV4SeverityEnums {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL"),
    ;

    private String value;

    CvssV4SeverityEnums(String value) {
        this.value = value;
    }
}
