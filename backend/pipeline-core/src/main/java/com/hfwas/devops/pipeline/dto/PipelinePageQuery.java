package com.hfwas.devops.pipeline.dto;

import lombok.Data;

@Data
public class PipelinePageQuery {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;

    public long resolvePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public long resolvePageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
