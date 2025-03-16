package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * @author hfwas
 * @package com.hfwas.devops.entity
 * @date 2025/3/16
 */
@TableName("devops_env")
public class DevopsEnv {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer type;

    @TableField("project_id")
    private Integer projectId;
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
