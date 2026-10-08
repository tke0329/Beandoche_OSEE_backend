package com.beandoche_osee_backend.domain.auth.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

public class AccessTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public AccessTokenProvider(String base64Secret, Duration accessTokenTtl, Clock clock) {
        this.signingKey = createSigningKey(base64Secret);
        this.accessTokenTtl = requirePositive(accessTokenTtl);
        this.clock = Objects.requireNonNull(clock);
    }

    public String issue(Long userId) {
        Objects.requireNonNull(userId);
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(signingKey)
                .compact();
    }

    public AccessTokenClaims parse(String token) {
        try {
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(requireToken(token));
            Claims claims = signedClaims.getPayload();
            validateTokenType(claims);

            return new AccessTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private static SecretKey createSigningKey(String base64Secret) {
        try {
            byte[] decodedSecret = Decoders.BASE64.decode(requireToken(base64Secret));
            return Keys.hmacShaKeyFor(decodedSecret);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT secret must be valid base64 with at least 256 bits", exception);
        }
    }

    private static Duration requirePositive(Duration duration) {
        Duration value = Objects.requireNonNull(duration);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        return value;
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        return token;
    }

    private static void validateTokenType(Claims claims) {
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new InvalidAccessTokenException();
        }
    }
}
