package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集合运行记录 VO
 *
 * @author hfwas
 */
@Data
public class CollectionRunVO {

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
    private LocalDateTime createTime;
}