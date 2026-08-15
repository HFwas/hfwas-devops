package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

import java.util.List;

/**
 * 集合运行详情 VO（含执行项结果）
 *
 * @author hfwas
 */
@Data
public class CollectionRunDetailVO {

    private Long id;
    private Long collectionId;
    private Long projectId;
    private Long environmentId;
    private String name;
    private String status;
    private Integer totalCount;
    private Integer passedCount;
    private Integer failedCount;
    private Integer errorCount;
    private Long durationMs;
    private String triggerMode;

    /** 执行项结果列表 */
    private List<CollectionRunItemVO> items;
}