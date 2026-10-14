package com.beandoche_osee_backend.domain.auth.provider.google;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.google")
public record GoogleOAuthProperties(
        String clientId,
        String issuer,
        String jwkSetUri,
        Duration connectTimeout,
        Duration readTimeout) {

    public GoogleOAuthProperties {
        clientId = requireText(clientId, "GOOGLE_OAUTH_CLIENT_ID");
        issuer = requireText(issuer, "Google issuer");
        jwkSetUri = requireText(jwkSetUri, "Google JWK Set URI");
        connectTimeout = requirePositive(connectTimeout, "Google connect timeout");
        readTimeout = requirePositive(readTimeout, "Google read timeout");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
