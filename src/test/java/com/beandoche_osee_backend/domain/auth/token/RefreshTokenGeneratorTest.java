package com.beandoche_osee_backend.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-07T00:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofHours(48);

    @Test
    @DisplayName("t1 refresh token separates the raw value and SHA-256 hash")
    void t1_refreshTokenSeparatesTheRawValueAndSha256Hash() {
        RefreshTokenGenerator generator = generatorAt(ISSUED_AT);

        IssuedRefreshToken token = generator.issue();

        assertThat(token.rawToken()).isNotBlank();
        assertThat(token.tokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(token.rawToken());
        assertThat(generator.hash(token.rawToken())).isEqualTo(token.tokenHash());
        assertThat(token.absoluteExpiresAt()).isEqualTo(ISSUED_AT.plus(SESSION_TTL));
        assertThat(token.expiresAt()).isEqualTo(token.absoluteExpiresAt());
    }

    @Test
    @DisplayName("t2 rotation preserves the original absolute expiration")
    void t2_rotationPreservesTheOriginalAbsoluteExpiration() {
        Instant absoluteExpiresAt = ISSUED_AT.plus(SESSION_TTL);
        RefreshTokenGenerator generator = generatorAt(ISSUED_AT.plus(Duration.ofHours(24)));

        IssuedRefreshToken rotatedToken = generator.rotate(absoluteExpiresAt);

        assertThat(rotatedToken.absoluteExpiresAt()).isEqualTo(absoluteExpiresAt);
        assertThat(rotatedToken.expiresAt()).isEqualTo(absoluteExpiresAt);
    }

    @Test
    @DisplayName("t3 rotation at the absolute expiration is rejected")
    void t3_rotationAtTheAbsoluteExpirationIsRejected() {
        Instant absoluteExpiresAt = ISSUED_AT.plus(SESSION_TTL);
        RefreshTokenGenerator generator = generatorAt(absoluteExpiresAt);

        assertThatThrownBy(() -> generator.rotate(absoluteExpiresAt))
                .isInstanceOf(RefreshSessionExpiredException.class)
                .hasMessage("Refresh session has expired");
    }

    private RefreshTokenGenerator generatorAt(Instant instant) {
        return new RefreshTokenGenerator(
                new SecureRandom(),
                Clock.fixed(instant, ZoneOffset.UTC),
                SESSION_TTL);
    }
}
