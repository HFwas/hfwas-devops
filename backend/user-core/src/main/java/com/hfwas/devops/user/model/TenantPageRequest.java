package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class TenantPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String keyword;
    /** all | 1 | 0 */
    private String status = "all";
}
