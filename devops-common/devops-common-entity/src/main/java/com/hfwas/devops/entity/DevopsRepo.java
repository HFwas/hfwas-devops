package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/6/18
 */
@TableName("devops_repo")
@Data
public class DevopsRepo {
    private Long id;
    private Long toolId;
    private Integer type;
    private String name;
    private String url;
    private String description;
    private Long   envId;
    private String storage;
    private LocalDateTime createTime;
    private Long createBy;
    private LocalDateTime updateTime;
    private Long updateBy;
    private Boolean deleted;
}
