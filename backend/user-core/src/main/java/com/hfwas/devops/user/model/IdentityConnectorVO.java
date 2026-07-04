package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityConnectorVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String type;
    private String typeLabel;
    /** Config with secrets masked. */
    private String configJson;
    private Integer enabled;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultTenantId;
    private String defaultTenantName;
    private Integer autoCreateMember;
    private LocalDateTime lastSyncTime;
    private String lastSyncStatus;
    private String lastSyncMessage;
    private Integer lastSyncCount;
    private LocalDateTime createTime;
}
