package com.beandoche_osee_backend.domain.auth.service;

import java.time.Instant;

public record GoogleLoginResult(
        Long userId,
        boolean newUser,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant sessionExpiresAt) {
}
