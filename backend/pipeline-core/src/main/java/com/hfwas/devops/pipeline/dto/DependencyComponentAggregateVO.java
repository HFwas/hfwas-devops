package com.hfwas.devops.pipeline.dto;

import lombok.Data;

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