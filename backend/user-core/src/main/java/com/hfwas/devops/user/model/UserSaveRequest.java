package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class UserSaveRequest {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String email;
    private String phone;
    private String role;
    private Integer enabled;
    private Long tenantId;
}
