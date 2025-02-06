package com.hfwas.devops.language.java;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class DepgraphDependency {
    private String from;
    private String to;
    private Long   numericFrom;
    private Long   numericTo;
    private String resolution;
}
