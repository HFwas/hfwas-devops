package com.hfwas.devops.pm.common;

import lombok.Data;

@Data
public class PmPageRequest {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
