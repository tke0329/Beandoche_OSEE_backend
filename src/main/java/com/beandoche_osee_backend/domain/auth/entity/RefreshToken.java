package com.beandoche_osee_backend.domain.auth.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(
            User user,
            String tokenHash,
            Instant absoluteExpiresAt,
            Instant expiresAt,
            Instant createdAt) {
        this.user = Objects.requireNonNull(user);
        this.tokenHash = requireHash(tokenHash);
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        validateExpiration(createdAt, expiresAt, absoluteExpiresAt);
    }

    public static RefreshToken issue(
            User user,
            String tokenHash,
            Instant absoluteExpiresAt,
            Instant expiresAt,
            Instant createdAt) {
        return new RefreshToken(user, tokenHash, absoluteExpiresAt, expiresAt, createdAt);
    }

    public void revoke(Instant revokedAt) {
        Instant value = Objects.requireNonNull(revokedAt);
        if (value.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        this.revokedAt = value;
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt) && now.isBefore(absoluteExpiresAt);
    }

    private static String requireHash(String value) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a 64 character SHA-256 hash");
        }
        return value;
    }

    private static void validateExpiration(Instant createdAt, Instant expiresAt, Instant absoluteExpiresAt) {
        if (!createdAt.isBefore(expiresAt) || expiresAt.isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException("refresh token expiration is invalid");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
