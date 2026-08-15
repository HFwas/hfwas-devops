package com.hfwas.devops.apitest.collection.vo;

import lombok.Data;

import java.util.List;

/**
 * 集合详情 VO（含文件夹树和集合项）
 *
 * @author hfwas
 */
@Data
public class CollectionDetailVO {

    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private Integer sortOrder;

    /** 文件夹树（根级节点） */
    private List<CollectionFolderVO> folders;

    /** 根级集合项（未归入文件夹的项） */
    private List<CollectionItemVO> items;
}