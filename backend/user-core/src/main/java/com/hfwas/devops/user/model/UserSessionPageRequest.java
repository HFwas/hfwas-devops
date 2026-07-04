package com.hfwas.devops.user.model;

import com.hfwas.devops.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserSessionPageRequest extends PageRequest {
    /** username / display name */
    private String keyword;
    /** online | idle | all */
    private String status = "all";
}
