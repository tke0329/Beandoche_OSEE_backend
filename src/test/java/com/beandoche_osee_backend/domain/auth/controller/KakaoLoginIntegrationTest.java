package com.beandoche_osee_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoApiClient;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.repository.SocialAccountRepository;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {"auth.kakao.rest-api-key=test-key", "auth.kakao.client-secret=test-secret",
        "auth.kakao.redirect-uri=http://localhost:8080/api/auth/social/kakao/callback", "auth.kakao.frontend-origin=http://localhost:5173"})
@AutoConfigureMockMvc
@Transactional
class KakaoLoginIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SocialAccountRepository accounts;
    @Autowired private RefreshTokenRepository tokens;
    @MockitoBean private KakaoApiClient client;

    @Test
    @DisplayName("t1 repeated verified Kakao callbacks preserve one user and hash stored refresh tokens")
    void t1_repeatedCallbacksPreserveOneUser() throws Exception {
        given(client.verifyCode("verified-code")).willReturn("kakao-123");
        completeLogin();
        completeLogin();
        assertThat(users.count()).isEqualTo(1);
        assertThat(accounts.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-123")).isPresent();
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(tokens.findAll()).allSatisfy(token -> assertThat(token.getTokenHash()).matches("[a-f0-9]{64}"));
    }

    private void completeLogin() throws Exception {
        var start = mvc.perform(get("/api/auth/social/kakao/authorize")).andExpect(status().isFound()).andReturn().getResponse();
        String state = UriComponentsBuilder.fromUriString(start.getHeader("Location")).build().getQueryParams().getFirst("state");
        var result = mvc.perform(get("/api/auth/social/kakao/callback").param("state", state).param("code", "verified-code")
                        .cookie(start.getCookie("osee_kakao_browser")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(result.getContentAsString()).contains("\"ok\":true").doesNotContain("refreshToken");
    }
}
