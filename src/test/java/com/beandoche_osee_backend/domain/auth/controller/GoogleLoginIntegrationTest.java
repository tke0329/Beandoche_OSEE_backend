package com.beandoche_osee_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdTokenVerifier;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdentity;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.repository.SocialAccountRepository;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(GoogleLoginIntegrationTest.FixedClockConfig.class)
class GoogleLoginIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary Clock integrationClock() {
            return Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
        }
    }

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
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Test
    @DisplayName("t1 first and repeated Google login preserve one account and hash refresh tokens")
    void t1_firstAndRepeatedGoogleLoginPreserveOneAccountAndHashRefreshTokens() throws Exception {
        given(googleIdTokenVerifier.verify("google-id-token"))
                .willReturn(new GoogleIdentity("google-subject-1"));

        MvcResult firstLogin = performLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andReturn();
        performLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        String rawRefreshToken = JsonPath.read(firstLogin.getResponse().getContentAsString(), "$.refreshToken");
        RefreshToken storedToken = refreshTokenRepository.findAll().getFirst();
        assertThat(storedToken.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(rawRefreshToken);
        assertThat(Duration.between(storedToken.getCreatedAt(), storedToken.getAbsoluteExpiresAt()))
                .isEqualTo(Duration.ofHours(48));
    }

    private org.springframework.test.web.servlet.ResultActions performLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/social/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idToken":"google-id-token"}
                        """));
    }
}
