package com.hfwas.devops.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/11
 */
@Data
@Getter
@Setter
public class DevopsVul {
    private Long id;
    private String schemaVersion;
    private String modified;
    private String published;
    private String cveId;
    private String ghsaId;
    private String details;
    private String serverity;
    private String affected;
    private String referencess;
    private String databaseSpecific;
    private String ecosystem;
    private String packages;
    private String introduced;
    private String fixed;
}
