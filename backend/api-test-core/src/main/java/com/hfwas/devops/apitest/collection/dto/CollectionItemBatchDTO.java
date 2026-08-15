package com.hfwas.devops.apitest.collection.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量添加集合项 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionItemBatchDTO {

    private Long folderId;

    @NotEmpty(message = "接口定义ID列表不能为空")
    private List<Long> definitionIds;
}