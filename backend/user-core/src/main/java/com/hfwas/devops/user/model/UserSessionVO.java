package com.hfwas.devops.user.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSessionVO {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String role;
    private String loginIp;
    private String clientInfo;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime expireTime;
    /** online | idle */
    private String onlineStatus;
    private Boolean current;
}
