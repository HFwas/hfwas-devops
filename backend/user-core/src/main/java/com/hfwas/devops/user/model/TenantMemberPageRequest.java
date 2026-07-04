package com.hfwas.devops.user.model;

import com.hfwas.devops.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TenantMemberPageRequest extends PageRequest {
    private String keyword;
}
