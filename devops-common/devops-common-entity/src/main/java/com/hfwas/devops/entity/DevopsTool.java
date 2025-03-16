package com.hfwas.devops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author houfei
 * @package com.hfwas.devops.entity
 * @date 2025/3/16
 */
@Data
@TableName("devops_tool")
public class DevopsTool {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer type;
    private String  name;
    private String  protocol;
    private String  ip;
    private Integer port;
    private String  username;
    private String  password;
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
