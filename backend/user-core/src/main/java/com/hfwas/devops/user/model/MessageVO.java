package com.hfwas.devops.user.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String displayName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;
    private String tenantName;
    private String category;
    private String categoryLabel;
    private String title;
    private String content;
    private Integer readFlag;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;
    private String senderName;
    private String bizType;
    private String bizId;
    private String linkUrl;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
