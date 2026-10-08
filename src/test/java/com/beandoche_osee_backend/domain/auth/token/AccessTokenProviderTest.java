package com.beandoche_osee_backend.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenProviderTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-07T00:00:00Z");
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("t1 issued access token contains the user and configured expiration")
    void t1_issuedAccessTokenContainsTheUserAndConfiguredExpiration() {
        AccessTokenProvider provider = providerAt(ISSUED_AT);

        String token = provider.issue(42L);
        AccessTokenClaims claims = provider.parse(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(claims.expiresAt()).isEqualTo(ISSUED_AT.plus(ACCESS_TOKEN_TTL));
    }

    @Test
    @DisplayName("t2 expired access token is rejected")
    void t2_expiredAccessTokenIsRejected() {
        String token = providerAt(ISSUED_AT).issue(42L);
        AccessTokenProvider expiredTokenProvider = providerAt(ISSUED_AT.plus(ACCESS_TOKEN_TTL).plusSeconds(1));

        assertThatThrownBy(() -> expiredTokenProvider.parse(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("t3 modified access token is rejected without exposing its value")
    void t3_modifiedAccessTokenIsRejectedWithoutExposingItsValue() {
        AccessTokenProvider provider = providerAt(ISSUED_AT);
        String token = provider.issue(42L);
        String[] tokenParts = token.split("\\.");
        String signature = tokenParts[2];
        String modifiedSignature = (signature.startsWith("a") ? "b" : "a") + signature.substring(1);
        String modifiedToken = tokenParts[0] + "." + tokenParts[1] + "." + modifiedSignature;

        assertThatThrownBy(() -> provider.parse(modifiedToken))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("Access token is invalid")
                .hasMessageNotContaining(token)
                .hasMessageNotContaining(modifiedToken);
    }

    private AccessTokenProvider providerAt(Instant instant) {
        return new AccessTokenProvider(
                JWT_SECRET,
                ACCESS_TOKEN_TTL,
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}
