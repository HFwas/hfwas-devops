package com.hfwas.devops.container.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("registry_info")
public class RegistryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String alias;
    private String type;           // "harbor" | "registry_v2"
    private String url;
    private Boolean insecure;
    private String credentialUsername;
    private String credentialPassword;  // AES-256-GCM encrypted
    private String source;         // "manual" | "builtin"
    private Long clusterId;
    private String status;         // Connected / Error / Unknown
    private String lastError;
    private String labels;         // JSON "{}"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}