package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集合 VO
 *
 * @author hfwas
 */
@Data
public class CollectionVO {

    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer folderCount;
    private Integer itemCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}