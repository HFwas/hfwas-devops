package com.hfwas.devops.user.model;

import com.hfwas.devops.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogPageRequest extends PageRequest {
    private String keyword;
    /** all | login_success | login_fail | logout */
    private String action = "all";
}
