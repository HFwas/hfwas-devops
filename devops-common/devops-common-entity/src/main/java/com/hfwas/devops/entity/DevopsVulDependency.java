package com.hfwas.devops.entity;

import lombok.Data;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/1/13
 */
@Data
public class DevopsVulDependency {
    private Long id;
    private Long gitId;
    private String company;
    private String dependencyName;
    private String version;
}
