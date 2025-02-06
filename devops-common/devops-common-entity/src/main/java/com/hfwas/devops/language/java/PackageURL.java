package com.hfwas.devops.language.java;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class PackageURL {
    private String type;
    
    private String namespace;

    private String name;
    
    private String version;

    private String qualifiers;

    private String subpath;
}
