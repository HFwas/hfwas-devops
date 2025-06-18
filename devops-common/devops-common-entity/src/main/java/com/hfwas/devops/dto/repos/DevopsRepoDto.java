package com.hfwas.devops.dto.repos;

import lombok.Data;

/**
 * @author hfwas
 * @package com.hfwas.devops.dto.repos
 * @date 2025/6/18
 */
@Data
public class DevopsRepoDto {
    private Long id;
    private Long toolId;
    private Integer type;
    private String name;
    private String description;
    private Long   envId;
    private String storage;
}
