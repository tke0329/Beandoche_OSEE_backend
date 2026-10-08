package com.beandoche_osee_backend.domain.auth.controller;

import java.time.Instant;

import com.beandoche_osee_backend.domain.auth.service.SocialLoginResult;

public record GoogleLoginResponse(
        Long userId,
        boolean newUser,
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant sessionExpiresAt) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static GoogleLoginResponse from(SocialLoginResult result) {
        return new GoogleLoginResponse(
                result.userId(),
                result.newUser(),
                BEARER_TOKEN_TYPE,
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshToken(),
                result.sessionExpiresAt());
    }
}
