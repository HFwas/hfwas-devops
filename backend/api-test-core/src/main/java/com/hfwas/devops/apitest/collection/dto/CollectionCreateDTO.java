package com.hfwas.devops.apitest.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建集合 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionCreateDTO {

    @NotBlank(message = "集合名称不能为空")
    private String name;

    private String description;

    private Integer sortOrder;
}