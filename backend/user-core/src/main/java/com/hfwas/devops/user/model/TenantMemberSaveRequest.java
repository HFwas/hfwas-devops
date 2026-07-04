package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class TenantMemberSaveRequest {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String tenantRole;
    private Integer status;
}
