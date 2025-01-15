package com.hfwas.devops.entity;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/15
 */
@Data
public class DevopsVulCodeVersion {
    private Long id;
    private Long depenVersionId;
    private Long codeId;
}
