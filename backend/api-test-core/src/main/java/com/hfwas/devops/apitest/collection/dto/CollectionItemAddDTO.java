package com.hfwas.devops.apitest.collection.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加集合项 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionItemAddDTO {

    private Long folderId;

    @NotNull(message = "接口定义ID不能为空")
    private Long definitionId;

    private String name;

    private String description;

    private Boolean enabled;
}