package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Platform-level role: admin | user. {@code tenantId} deprecated, use tenant membership. */
    private Long tenantId;
    private String username;
    private String password;
    private String displayName;
    private String email;
    private String phone;
    /** admin | user */
    private String role;
    /** local | ldap | oauth2 */
    private String authSource;
    /** External directory id when synced from connector. */
    private String externalId;
    /** Source connector id; null for local users. */
    private Long connectorId;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
