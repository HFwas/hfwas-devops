package com.hfwas.devops.entity;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/11
 */
@Data
public class DevopsVul {
    private Long id;
    private String schemaVersion;
    private String modified;
    private String published;
    private String aliases;
    private String details;
    private String severity;
    private String affected;
    private String references;
    private String databaseSpecific;
}
