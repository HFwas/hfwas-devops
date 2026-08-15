package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

/**
 * 集合项 VO
 *
 * @author hfwas
 */
@Data
public class CollectionItemVO {

    private Long id;
    private Long collectionId;
    private Long folderId;
    private Long definitionId;
    private String name;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;

    /** 接口定义信息（冗余展示） */
    private String method;
    private String path;
}