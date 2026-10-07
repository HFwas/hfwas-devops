package com.hfwas.devops.container.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("helm_chart_repository")
public class HelmChartRepositoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    /** oci. P0 only persists Harbor OCI targets. */
    private String type;
    /** Canonical registry base, for example oci://harbor.example/charts */
    private String url;
    private Boolean insecure;
    /** Reserved for a later credential binding. P0 reads username/password from config. */
    private String credentialId;
    private LocalDateTime createdAt;
}
