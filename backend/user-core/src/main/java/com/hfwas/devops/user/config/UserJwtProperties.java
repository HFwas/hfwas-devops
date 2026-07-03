package com.hfwas.devops.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "user.jwt")
public class UserJwtProperties {
    private String secret = "hfwas-devops-user-jwt-secret-key-change-in-production";
    private long expireSeconds = 86400;
}
