package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/1/24
 */
@Data
@TableName(value = "devops_vul_packages")
public class DevopsVulPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vulId;
    private String ecosystem;
    private String packages;
    private String introduced;
    private String fixed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
