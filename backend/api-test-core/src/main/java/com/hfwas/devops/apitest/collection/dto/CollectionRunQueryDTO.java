package com.hfwas.devops.apitest.collection.dto;

import lombok.Data;

/**
 * 集合运行历史查询 DTO
 *
 * @author hfwas
 */
@Data
public class CollectionRunQueryDTO {

    private Long collectionId;

    private Integer pageNo = 1;

    private Integer pageSize = 20;

    public long resolvePageNo() {
        return pageNo != null && pageNo > 0 ? pageNo : 1;
    }

    public long resolvePageSize() {
        return pageSize != null && pageSize > 0 ? pageSize : 20;
    }
}