package com.hfwas.devops.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DependencyComponentVO {
    private Long id;
    private Long artifactId;
    private Long runId;
    private String purl;
    private String groupName;
    private String name;
    private String version;
    private String license;
    private String scope;
    private String language;
    private Long pipelineId;
    private String pipelineName;
    private String repoUrl;
    private LocalDateTime createTime;
}

@Data
public class DependencyComponentAggregateVO {
    private String purl;
    private String groupName;
    private String name;
    private String version;
    private String license;
    private String language;
    private int occurrenceCount;       // 出现在多少个运行中
    private String[] usedInRepos;      // 示例仓库名
}