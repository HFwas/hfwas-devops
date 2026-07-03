package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class LoginLogPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String keyword;
    /** all | login_success | login_fail | logout */
    private String action = "all";
}
