package com.hfwas.devops.user.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String action;
    private String loginIp;
    private String clientInfo;
    private String userAgent;
    private String failReason;
    private LocalDateTime createTime;
}
