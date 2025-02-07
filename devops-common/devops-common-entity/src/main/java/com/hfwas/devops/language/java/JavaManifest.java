package com.hfwas.devops.language.java;

import lombok.Data;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class JavaManifest {
    private Map<String, JavaDependency> resolved;
    private String                      name;
    private InputStream                 file;

    public JavaManifest(String projectName) {
        this.resolved = new HashMap<>();
    }

    public JavaManifest(String projectName, String filePath) {
        this.resolved = new HashMap<>();
    }

    public void addDirectDependency(JavaPackage pkg, DependencyScope scope) {
        resolved.put(pkg.packageID(), new JavaDependency(pkg, DependencyRelationship.direct, scope));
    }

    public void addIndirectDependency(JavaPackage pkg, DependencyScope scope) {
        resolved.putIfAbsent(pkg.packageID(), new JavaDependency(pkg, DependencyRelationship.indirect, scope));
    }

}
