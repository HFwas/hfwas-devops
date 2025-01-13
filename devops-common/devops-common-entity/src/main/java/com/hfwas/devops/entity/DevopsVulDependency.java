package com.hfwas.devops.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/1/13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DevopsVulDependency {
    private Long id;
    private Long gitId;
    private String company;
    private String dependencyName;
    private String version;
}
