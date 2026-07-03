package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Unique slug for login resolution, e.g. default, acme */
    private String code;
    private String name;
    private String contactName;
    private String contactPhone;
    /** 1=enabled, 0=disabled */
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
