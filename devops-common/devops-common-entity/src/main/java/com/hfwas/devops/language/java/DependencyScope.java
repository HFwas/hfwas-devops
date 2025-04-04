package com.hfwas.devops.language.java;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Getter
@AllArgsConstructor
public enum DependencyScope {
    runtime("runtime"),
    development("development"),
    ;

    private String scope;
}
