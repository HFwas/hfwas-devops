package com.hfwas.devops.language.java;

import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class Depgraph {
    private String                   graphName;
    private List<DepgraphArtifact>   artifacts;
    private List<DepgraphDependency> dependencies;
    private Boolean                  isMultiModule;
}
