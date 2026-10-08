package com.beandoche_osee_backend.domain.auth.provider.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleAudienceValidatorTest {

    private final GoogleAudienceValidator validator = new GoogleAudienceValidator("osee-google-client-id");

    @Test
    @DisplayName("t1 configured Google client id is accepted as an audience")
    void t1_configuredGoogleClientIdIsAcceptedAsAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("osee-google-client-id")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("t2 a different audience is rejected")
    void t2_differentAudienceIsRejected() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("other-client-id")));

        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt jwt(List<String> audience) {
        Instant issuedAt = Instant.parse("2026-10-08T00:00:00Z");
        return new Jwt(
                "redacted-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "google-subject-1", "aud", audience));
    }
}
