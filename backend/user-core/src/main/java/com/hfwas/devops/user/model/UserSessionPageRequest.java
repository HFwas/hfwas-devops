package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class UserSessionPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    /** username / display name */
    private String keyword;
    /** online | idle | all */
    private String status = "all";
}
