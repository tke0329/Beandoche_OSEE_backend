package com.beandoche_osee_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoLoginStateStore;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoOAuthProperties;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.service.KakaoLoginApplicationService;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginResult;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.global.security.RestAuthenticationEntryPoint;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@WebMvcTest(KakaoLoginController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({KakaoLoginStateStore.class, KakaoLoginControllerTest.Config.class})
class KakaoLoginControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private KakaoLoginApplicationService service;
    @MockitoBean private AccessTokenProvider accessTokenProvider;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC); }
        @Bean KakaoOAuthProperties properties() { return new KakaoOAuthProperties("key", "secret", "http://localhost:8080/api/auth/social/kakao/callback", "http://localhost:5173"); }
    }

    @Test
    @DisplayName("t1 authorization redirects to Kakao with state and HttpOnly browser binding")
    void t1_authorizationBindsBrowser() throws Exception {
        var response = mvc.perform(get("/api/auth/social/kakao/authorize")).andExpect(status().isFound()).andReturn().getResponse();
        assertThat(URI.create(response.getHeader("Location")).getHost()).isEqualTo("kauth.kakao.com");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax", "Max-Age=300");
        assertThat(response.getHeader("Location")).doesNotContain("secret");
    }

    @Test
    @DisplayName("t2 successful callback posts service access token to fixed frontend origin without exposing refresh token")
    void t2_successfulCallbackPostsSafeSession() throws Exception {
        var start = mvc.perform(get("/api/auth/social/kakao/authorize")).andReturn().getResponse();
        String state = UriComponentsBuilder.fromUriString(start.getHeader("Location")).build().getQueryParams().getFirst("state");
        Cookie binding = start.getCookie("osee_kakao_browser");
        given(service.login("valid-code")).willReturn(new SocialLoginResult(42L, true, "service-access", Instant.parse("2026-10-15T00:15:00Z"), "sensitive-refresh", Instant.parse("2026-10-17T00:00:00Z")));
        var response = mvc.perform(get("/api/auth/social/kakao/callback").param("state", state).param("code", "valid-code").cookie(binding))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("osee:kakao-login", "service-access", "http://localhost:5173").doesNotContain("sensitive-refresh", "valid-code");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'", "script-src 'nonce-");
    }

    @Test
    @DisplayName("t3 invalid state cannot call provider or create a session")
    void t3_invalidStateCannotCreateSession() throws Exception {
        var response = mvc.perform(get("/api/auth/social/kakao/callback").param("state", "forged").param("code", "sensitive-code"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("KAKAO_STATE_INVALID").doesNotContain("sensitive-code");
        verifyNoInteractions(service);
    }
}
