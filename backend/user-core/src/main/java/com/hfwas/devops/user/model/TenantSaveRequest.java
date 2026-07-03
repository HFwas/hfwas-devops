package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class TenantSaveRequest {
    private Long id;
    private String code;
    private String name;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private String remark;
}
