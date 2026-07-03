package com.hfwas.devops.user.security;

import com.hfwas.devops.user.config.UserJwtProperties;
import com.hfwas.devops.user.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtTokenService(UserJwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = properties.getExpireSeconds();
    }

    public IssuedToken issueToken(SysUser user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(expireSeconds);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expireAt);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userIdFromToken(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public Optional<String> jtiFromToken(String token) {
        String jti = parseToken(token).getId();
        return StringUtils.hasText(jti) ? Optional.of(jti) : Optional.empty();
    }

    /** Stable session key: JWT jti, or derived key for legacy tokens without jti. */
    public String resolveSessionKey(String token) {
        Claims claims = parseToken(token);
        return resolveSessionKey(token, claims);
    }

    public String resolveSessionKey(String token, Claims claims) {
        String jti = claims.getId();
        if (StringUtils.hasText(jti)) {
            return jti;
        }
        return legacySessionKey(token);
    }

    private static String legacySessionKey(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("legacy-");
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "legacy-" + Integer.toHexString(token.hashCode());
        }
    }
}
