package com.beandoche_osee_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.RefreshTokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebSessionServiceTest {
    private final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenGenerator generator = new RefreshTokenGenerator(new SecureRandom(), clock, Duration.ofHours(48));
    private final AccessTokenProvider access = new AccessTokenProvider("dGVzdC1zZWNyZXQta2V5LXdpdGgtYXQtbGVhc3QtMzItYnl0ZXM=", Duration.ofMinutes(15), clock);

    @Test @DisplayName("t1 rotation revokes old token and preserves absolute expiry")
    void t1_rotationPreservesAbsoluteExpiry() {
        User user = mock(User.class);
        given(user.isActive()).willReturn(true);
        given(user.getId()).willReturn(42L);
        var expiry = now.plusSeconds(20);
        var token = RefreshToken.issue(user, generator.hash("raw-token"), expiry, expiry, now.minusSeconds(100));
        given(repository.findLockedByTokenHash(generator.hash("raw-token"))).willReturn(Optional.of(token));
        var result = new WebSessionService(repository, generator, access, clock).rotate("raw-token").orElseThrow();
        assertThat(result.sessionExpiresAt()).isEqualTo(expiry);
        assertThat(result.accessTokenExpiresAt()).isEqualTo(expiry);
        assertThat(result.refreshToken()).isNotEqualTo("raw-token");
        assertThat(token.isUsableAt(now)).isFalse();
    }

    @Test @DisplayName("t2 expired and revoked refresh tokens cannot restore a session")
    void t2_expiredTokenCannotRestoreSession() {
        var token = RefreshToken.issue(User.active(now.minusSeconds(100)), generator.hash("expired"), now, now, now.minusSeconds(100));
        given(repository.findLockedByTokenHash(generator.hash("expired"))).willReturn(Optional.of(token));
        assertThat(new WebSessionService(repository, generator, access, clock).rotate("expired")).isEmpty();
    }

    @Test @DisplayName("t3 logout revokes the current refresh token")
    void t3_logoutRevokesCurrentToken() {
        var token = RefreshToken.issue(User.active(now), generator.hash("current"), now.plusSeconds(100), now.plusSeconds(100), now);
        given(repository.findLockedByTokenHash(generator.hash("current"))).willReturn(Optional.of(token));
        new WebSessionService(repository, generator, access, clock).logout("current");
        assertThat(token.getRevokedAt()).isEqualTo(now);
    }
}
