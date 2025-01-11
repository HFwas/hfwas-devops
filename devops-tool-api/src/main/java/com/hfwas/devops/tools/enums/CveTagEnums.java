package com.hfwas.devops.tools.enums;

import lombok.Getter;

@Getter
public enum CveTagEnums {
    disputed("disputed"),
    assigned("unsupported-when-assigned"),
    service("exclusively-hosted-service"),
    ;

    private String name;

    CveTagEnums(String name) {
        this.name = name;
    }
}
