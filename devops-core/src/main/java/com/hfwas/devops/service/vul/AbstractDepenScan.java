package com.hfwas.devops.service.vul;

import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul
 * @date 2025/1/13
 */
public abstract class AbstractDepenScan implements DevopsDepenScan {

    @Override
    public String language() {
        return "java";
    }

    @Override
    public String type() {
        return "maven";
    }

    @Override
    public List<String> depenVersion(String depen, String version) {
        return List.of();
    }
}
