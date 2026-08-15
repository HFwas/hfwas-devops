package com.hfwas.devops.apitest.collection.dto;

import lombok.Data;

/**
 * 更新文件夹 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionFolderUpdateDTO {

    private String name;

    private String description;

    private Integer sortOrder;
}