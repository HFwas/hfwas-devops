package com.hfwas.devops.language.java;

import lombok.AllArgsConstructor;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@AllArgsConstructor
public enum DependencyScope {
    RUNTIME("runtime"),
    DEVELOPMENT("development"),
    ;

    private String scope;
}
