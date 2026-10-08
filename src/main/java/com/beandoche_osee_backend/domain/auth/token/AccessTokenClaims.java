package com.beandoche_osee_backend.domain.auth.token;

import java.time.Instant;

public record AccessTokenClaims(Long userId, Instant issuedAt, Instant expiresAt) {
}
