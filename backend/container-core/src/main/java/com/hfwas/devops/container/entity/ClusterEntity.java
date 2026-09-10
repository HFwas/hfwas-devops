package com.hfwas.devops.container.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cluster_info")
public class ClusterEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String alias;
    private String provider;
    private String version;
    private String kubeconfig;   // AES-256-GCM encrypted
    private String mode;         // Phase 1: only "proxy"
    private String status;       // Connected / Degraded / Disconnected / Unknown
    private String labels;       // JSON "{}"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}