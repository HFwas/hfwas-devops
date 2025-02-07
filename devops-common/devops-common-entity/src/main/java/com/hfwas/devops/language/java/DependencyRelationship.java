package com.hfwas.devops.language.java;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/7
 */
@AllArgsConstructor
@Getter
public enum DependencyRelationship {
    direct("direct"),
    indirect("indirect"),
    ;

    private String relation;

}
