package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class TenantMemberPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String keyword;
}
