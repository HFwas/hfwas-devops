package com.hfwas.devops.tools.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CvssV3SeverityEnums {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL"),
    ;

    private String value;
}