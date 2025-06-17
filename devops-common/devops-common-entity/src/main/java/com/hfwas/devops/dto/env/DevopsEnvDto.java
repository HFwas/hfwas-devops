package com.hfwas.devops.dto.env;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.dto.env
 * @date 2025/6/12
 */
@Data
public class DevopsEnvDto {
    private Long   id;
    private String name;
    private String code;
    private String description;
    /**
     * 环境类型，1、dev，2、uat，3、pre、4、prod
     */
    private Integer type;
    private Long projectId;
}
