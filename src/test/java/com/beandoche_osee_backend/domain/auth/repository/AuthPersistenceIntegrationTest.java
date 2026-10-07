package com.beandoche_osee_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;

import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import com.beandoche_osee_backend.domain.auth.entity.SocialAccount;
import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.entity.UserStatus;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@Transactional
class AuthPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("t1 Flyway creates all authentication tables")
    void t1_flywayCreatesAllAuthenticationTables() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'social_account', 'refresh_token')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    @DisplayName("t2 provider and provider user id cannot be duplicated")
    void t2_providerAndProviderUserIdCannotBeDuplicated() {
        Long userId = insertActiveUser();
        insertSocialAccount(userId, "GOOGLE", "provider-user-1");

        assertThatThrownBy(() -> insertSocialAccount(userId, "GOOGLE", "provider-user-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t3 refresh token hashes cannot be duplicated")
    void t3_refreshTokenHashesCannotBeDuplicated() {
        Long userId = insertActiveUser();
        insertRefreshToken(userId, "same-token-hash");

        assertThatThrownBy(() -> insertRefreshToken(userId, "same-token-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t4 repositories persist and find authentication aggregates")
    void t4_repositoriesPersistAndFindAuthenticationAggregates() {
        Instant now = Instant.now();
        User user = userRepository.save(User.active(now));
        socialAccountRepository.save(SocialAccount.link(user, SocialProvider.GOOGLE, "google-user-1", now));
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                "a".repeat(64),
                now.plusSeconds(172_800),
                now.plusSeconds(172_800),
                now));

        User savedUser = userRepository.findById(user.getId()).orElseThrow();
        SocialAccount savedAccount = socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-user-1")
                .orElseThrow();
        RefreshToken savedToken = refreshTokenRepository.findByTokenHash("a".repeat(64)).orElseThrow();

        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedAccount.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedToken.getAbsoluteExpiresAt()).isEqualTo(now.plusSeconds(172_800));
    }

    private Long insertActiveUser() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (status, created_at)
                VALUES ('ACTIVE', ?)
                RETURNING id
                """, Long.class, Timestamp.from(Instant.now()));
    }

    private void insertSocialAccount(Long userId, String provider, String providerUserId) {
        jdbcTemplate.update("""
                INSERT INTO social_account (user_id, provider, provider_user_id, created_at)
                VALUES (?, ?, ?, ?)
                """, userId, provider, providerUserId, Timestamp.from(Instant.now()));
    }

    private void insertRefreshToken(Long userId, String tokenHash) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO refresh_token (user_id, token_hash, absolute_expires_at, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, tokenHash, Timestamp.from(now.plusSeconds(172_800)),
                Timestamp.from(now.plusSeconds(172_800)), Timestamp.from(now));
    }
}
