package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/3/16
 */
@TableName("devops_repo")
public class DevopsRepo {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String url;
    private String desc;
    private Integer type;
    private String format;

    @TableField("tenant_id")
    private Integer tenantId;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Integer createBy;
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private Integer updateBy;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(value = "del_flag")
    @TableLogic
    private Integer delFlag;
}
