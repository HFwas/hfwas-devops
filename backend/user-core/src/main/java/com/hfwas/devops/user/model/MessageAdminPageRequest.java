package com.hfwas.devops.user.model;

import com.hfwas.devops.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MessageAdminPageRequest extends PageRequest {
    private Long userId;
    /** all | system | operation | announcement */
    private String category = "all";
    private String keyword;
}
