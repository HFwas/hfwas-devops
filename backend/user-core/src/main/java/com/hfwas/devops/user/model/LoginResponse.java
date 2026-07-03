package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserProfile user;
}
