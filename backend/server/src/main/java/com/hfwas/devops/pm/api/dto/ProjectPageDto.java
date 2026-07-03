package com.hfwas.devops.pm.api.dto;

import com.hfwas.devops.pm.common.PmPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageDto extends PmPageRequest {
    private String keyword;
}
