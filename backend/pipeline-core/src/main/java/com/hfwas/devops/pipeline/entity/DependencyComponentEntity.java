package com.hfwas.devops.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dependency_component")
public class DependencyComponentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long artifactId;
    private Long runId;
    private Long pipelineId;
    /** 去重键：有 purl 用 purl，否则 gav:group|name|version */
    private String identityKey;
    private String purl;
    private String groupName;
    private String name;
    private String version;
    private String license;
    private String scope;
    private String language;
    private LocalDateTime createTime;
}