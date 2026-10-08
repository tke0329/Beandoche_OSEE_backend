package com.beandoche_osee_backend.domain.auth.token;

import java.time.Instant;

public record IssuedRefreshToken(
        String rawToken,
        String tokenHash,
        Instant absoluteExpiresAt,
        Instant expiresAt) {
}
