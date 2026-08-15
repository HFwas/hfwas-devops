package com.hfwas.devops.apitest.collection.dto;

import lombok.Data;

/**
 * 更新集合 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionUpdateDTO {

    private String name;

    private String description;

    private Integer sortOrder;
}