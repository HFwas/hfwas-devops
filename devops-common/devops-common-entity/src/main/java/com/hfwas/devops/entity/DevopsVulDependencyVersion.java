package com.hfwas.devops.entity;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/15
 */
@Data
public class DevopsVulDependencyVersion {
    private Long id;
    private Long depenId;
    private Long version;
}
