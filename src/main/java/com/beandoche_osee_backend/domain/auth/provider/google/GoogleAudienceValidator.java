package com.beandoche_osee_backend.domain.auth.provider.google;

import java.util.Objects;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class GoogleAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "The Google ID token audience is invalid.",
            null);

    private final String clientId;

    public GoogleAudienceValidator(String clientId) {
        this.clientId = requireText(clientId, "Google OAuth client ID");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience().contains(clientId)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name + " must be configured");
        if (text.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return text;
    }
}
