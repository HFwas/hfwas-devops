package com.hfwas.devops.language.java;

import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class DepgraphArtifact {
    private String       id;
    private Long         numericId;
    private String       groupId;
    private String       artifactId;
    private String       version;
    private Boolean      optional;
    private List<String> scopes;
    private List<String> types;
    private List<String> classifiers;
}
