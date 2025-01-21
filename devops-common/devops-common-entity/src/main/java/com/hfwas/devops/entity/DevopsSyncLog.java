package com.hfwas.devops.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/21
 */
@Data
public class DevopsSyncLog {
    private Long id;
    private Integer type;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer success_num;
    private String success_data;
    private Integer error_num;
    private String error_data;
}
