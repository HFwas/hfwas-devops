package cn.hfwas.devops.tools.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CveTagEnums {
    disputed("disputed"),
    assigned("unsupported-when-assigned"),
    service("exclusively-hosted-service"),
    ;

    private String name;
}
