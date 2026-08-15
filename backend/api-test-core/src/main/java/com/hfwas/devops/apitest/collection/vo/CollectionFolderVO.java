package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

import java.util.List;

/**
 * 集合文件夹 VO（树形结构）
 *
 * @author hfwas
 */
@Data
public class CollectionFolderVO {

    private Long id;
    private Long collectionId;
    private Long parentId;
    private String name;
    private String description;
    private Integer sortOrder;

    /** 子文件夹 */
    private List<CollectionFolderVO> children;

    /** 当前文件夹下的集合项 */
    private List<CollectionItemVO> items;
}