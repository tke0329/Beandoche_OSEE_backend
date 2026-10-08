package com.beandoche_osee_backend.domain.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

public class RefreshTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration sessionTtl;

    public RefreshTokenGenerator(SecureRandom secureRandom, Clock clock, Duration sessionTtl) {
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.clock = Objects.requireNonNull(clock);
        this.sessionTtl = requirePositive(sessionTtl);
    }

    public IssuedRefreshToken issue() {
        Instant issuedAt = clock.instant();
        return createToken(issuedAt.plus(sessionTtl));
    }

    public IssuedRefreshToken rotate(Instant absoluteExpiresAt) {
        Instant absoluteExpiration = Objects.requireNonNull(absoluteExpiresAt);
        if (!clock.instant().isBefore(absoluteExpiration)) {
            throw new RefreshSessionExpiredException();
        }
        return createToken(absoluteExpiration);
    }

    public String hash(String rawToken) {
        String value = requireText(rawToken);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private IssuedRefreshToken createToken(Instant absoluteExpiresAt) {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = minimum(clock.instant().plus(sessionTtl), absoluteExpiresAt);

        return new IssuedRefreshToken(rawToken, hash(rawToken), absoluteExpiresAt, expiresAt);
    }

    private static Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static Duration requirePositive(Duration duration) {
        Duration value = Objects.requireNonNull(duration);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive");
        }
        return value;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be blank");
        }
        return value;
    }
}
