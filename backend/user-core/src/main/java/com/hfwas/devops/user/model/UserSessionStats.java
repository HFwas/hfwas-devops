package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class UserSessionStats {
    private long onlineCount;
    private long idleCount;
    private long totalActive;
}
