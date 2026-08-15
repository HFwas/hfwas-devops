package com.hfwas.devops.apitest.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建文件夹 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionFolderCreateDTO {

    private Long parentId;

    @NotBlank(message = "文件夹名称不能为空")
    private String name;

    private String description;

    private Integer sortOrder;
}