package com.hfwas.devops.user.security;

import java.time.Instant;

public record IssuedToken(String token, String jti, Instant expireAt) {
}
