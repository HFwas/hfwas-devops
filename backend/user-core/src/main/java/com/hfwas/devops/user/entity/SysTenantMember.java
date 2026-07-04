package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant_member")
public class SysTenantMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** tenant_admin | member */
    private String tenantRole;
    /** 1=active in tenant, 0=disabled in tenant */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;
    @TableLogic
    private Integer delFlag;
}
