package com.beandoche_osee_backend.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.token")
public record AuthTokenProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration sessionTtl) {

    public AuthTokenProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET must be configured");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive");
        }
    }
}
