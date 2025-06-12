package com.hfwas.devops.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/6/12
 */
@Data
public class DevopsEnvInstance {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer source;
    private Integer type;
    private Long    envId;
    private Integer delFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
