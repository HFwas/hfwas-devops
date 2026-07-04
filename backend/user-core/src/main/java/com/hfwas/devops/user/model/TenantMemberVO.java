package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantMemberVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    /** tenant_admin | member */
    private String tenantRole;
    private Integer status;
    private LocalDateTime joinTime;
}
