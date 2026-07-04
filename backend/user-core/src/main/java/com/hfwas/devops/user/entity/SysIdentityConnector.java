package com.hfwas.devops.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_identity_connector")
public class SysIdentityConnector {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** ldap | oauth2 | ... */
    private String type;
    /** JSON config; sensitive fields stored as plain text for now (admin-only access). */
    private String configJson;
    private Integer enabled;
    /** Target tenant for auto membership on sync. */
    private Long defaultTenantId;
    /** 1 = add synced users as tenant members automatically. */
    private Integer autoCreateMember;
    private LocalDateTime lastSyncTime;
    /** success | fail | partial */
    private String lastSyncStatus;
    private String lastSyncMessage;
    private Integer lastSyncCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
