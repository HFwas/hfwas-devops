package com.hfwas.devops.user.operlog.model;

import com.hfwas.devops.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperLogPageRequest extends PageRequest {
    private String keyword;
    private String module = "all";
    private String action;
}
