package com.hfwas.devops.vo.env;

import lombok.Data;

/**
 * @author hfwas
 * @package com.hfwas.devops.vo.env
 * @date 2025/6/12
 */
@Data
public class DevopsEnvVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer type;
    private Integer projectId;
}
