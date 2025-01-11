package com.hfwas.devops.tools.enums;

import lombok.Getter;

@Getter
public enum CvssV2SeverityEnums {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    ;

    private String value;

    CvssV2SeverityEnums(String value) {
        this.value = value;
    }
}


