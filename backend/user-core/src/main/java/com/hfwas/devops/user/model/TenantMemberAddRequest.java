package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class TenantMemberAddRequest {
    private List<Long> userIds;
    /** tenant_admin | member */
    private String tenantRole;
}
