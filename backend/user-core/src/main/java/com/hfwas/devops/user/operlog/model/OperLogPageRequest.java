package com.hfwas.devops.user.operlog.model;

import lombok.Data;

@Data
public class OperLogPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String keyword;
    private String module = "all";
    private String action;
}
