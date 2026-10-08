package com.beandoche_osee_backend.global.security;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenClaims;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.InvalidAccessTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProtectedTestController.class)
@Import({SecurityConfig.class, BearerAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class SecurityAuthenticationTest {

    private static final String PROTECTED_PATH = "/api/test/protected";
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("t1 request without an access token returns a safe authentication error")
    void t1_requestWithoutAccessTokenReturnsSafeAuthenticationError() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    @DisplayName("t2 invalid access token returns a safe token error without echoing the token")
    void t2_invalidAccessTokenReturnsSafeTokenErrorWithoutEchoingTheToken() throws Exception {
        String rawToken = "invalid-sensitive-token";
        given(accessTokenProvider.parse(rawToken)).willThrow(new InvalidAccessTokenException());

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."))
                .andExpect(content().string(not(containsString(rawToken))))
                .andExpect(content().string(not(containsString("InvalidAccessTokenException"))));
    }

    @Test
    @DisplayName("t3 active user with a valid access token can access protected resources")
    void t3_activeUserWithValidAccessTokenCanAccessProtectedResources() throws Exception {
        User activeUser = mock(User.class);
        given(activeUser.isActive()).willReturn(true);
        given(activeUser.getId()).willReturn(42L);
        given(accessTokenProvider.parse("active-token"))
                .willReturn(new AccessTokenClaims(42L, NOW, NOW.plusSeconds(900)));
        given(userRepository.findById(42L)).willReturn(Optional.of(activeUser));

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer active-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    @DisplayName("t4 suspended user is rejected even with a valid access token")
    void t4_suspendedUserIsRejectedEvenWithValidAccessToken() throws Exception {
        User suspendedUser = User.active(NOW);
        suspendedUser.suspend();
        given(accessTokenProvider.parse("suspended-token"))
                .willReturn(new AccessTokenClaims(42L, NOW, NOW.plusSeconds(900)));
        given(userRepository.findById(42L)).willReturn(Optional.of(suspendedUser));

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer suspended-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    @DisplayName("t5 withdrawn user is rejected even with a valid access token")
    void t5_withdrawnUserIsRejectedEvenWithValidAccessToken() throws Exception {
        User withdrawnUser = User.active(NOW);
        withdrawnUser.withdraw(NOW.plusSeconds(1));
        given(accessTokenProvider.parse("withdrawn-token"))
                .willReturn(new AccessTokenClaims(42L, NOW, NOW.plusSeconds(900)));
        given(userRepository.findById(42L)).willReturn(Optional.of(withdrawnUser));

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer withdrawn-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    @DisplayName("t6 public authentication endpoint ignores an obsolete access token")
    void t6_publicAuthenticationEndpointIgnoresObsoleteAccessToken() throws Exception {
        String obsoleteToken = "obsolete-token";
        given(accessTokenProvider.parse(obsoleteToken)).willThrow(new InvalidAccessTokenException());

        mockMvc.perform(get("/api/auth/test/public")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + obsoleteToken))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    @DisplayName("t7 bearer scheme is accepted without case sensitivity")
    void t7_bearerSchemeIsAcceptedWithoutCaseSensitivity() throws Exception {
        User activeUser = mock(User.class);
        given(activeUser.isActive()).willReturn(true);
        given(activeUser.getId()).willReturn(42L);
        given(accessTokenProvider.parse("active-token"))
                .willReturn(new AccessTokenClaims(42L, NOW, NOW.plusSeconds(900)));
        given(userRepository.findById(42L)).willReturn(Optional.of(activeUser));

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "bearer active-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }
}
