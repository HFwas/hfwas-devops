package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    private String name;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private String remark;
    private long userCount;
    private long projectCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
