package com.hfwas.devops.user.operlog.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String module;
    private String action;
    private String bizType;
    private String bizId;
    private String summary;
    private String status;
    private String failReason;
    private String requestIp;
    private String clientInfo;
    private String userAgent;
    private String extraJson;
    private LocalDateTime createTime;
}
