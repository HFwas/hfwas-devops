package com.hfwas.devops.common.page;

import lombok.Data;

/**
 * Standard pagination request: pageNo (1-based), pageSize.
 */
@Data
public class PageRequest {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    private Integer pageNo = DEFAULT_PAGE_NO;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public int resolvePageNo() {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
