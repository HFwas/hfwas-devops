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
public class Manifest {
    private Map<String, Object> resolved;
    private String              name;
    private InputStream         file;

    public Manifest(String projectName) {
        this.resolved = new HashMap<>();
    }

    public Manifest(String projectName, String filePath) {
        this.resolved = new HashMap<>();
    }

}
