package com.hfwas.devops.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/13
 */
@Data
@EqualsAndHashCode(of = {"id","name","description"})
public class DevopsVulCwe {
    private Long id;
    private String name;
    private String description;
    private String extendedDescription;
    private Integer type;
}
