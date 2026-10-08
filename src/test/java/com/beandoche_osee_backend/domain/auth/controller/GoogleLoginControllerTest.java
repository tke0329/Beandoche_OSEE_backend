package com.beandoche_osee_backend.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.service.GoogleLoginResult;
import com.beandoche_osee_backend.domain.auth.service.GoogleLoginService;
import com.beandoche_osee_backend.domain.auth.service.InvalidSocialTokenException;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.global.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GoogleLoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class GoogleLoginControllerTest {

    private static final Instant ACCESS_TOKEN_EXPIRES_AT = Instant.parse("2026-10-08T00:15:00Z");
    private static final Instant SESSION_EXPIRES_AT = Instant.parse("2026-10-10T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleLoginService googleLoginService;

    @MockitoBean
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Test
    @DisplayName("t1 valid Google id token returns service tokens")
    void t1_validGoogleIdTokenReturnsServiceTokens() throws Exception {
        given(googleLoginService.login("valid-google-id-token")).willReturn(new GoogleLoginResult(
                42L,
                true,
                "access-token",
                ACCESS_TOKEN_EXPIRES_AT,
                "refresh-token",
                SESSION_EXPIRES_AT));

        mockMvc.perform(post("/api/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"valid-google-id-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").value(ACCESS_TOKEN_EXPIRES_AT.toString()))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.sessionExpiresAt").value(SESSION_EXPIRES_AT.toString()));
    }

    @Test
    @DisplayName("t2 blank Google id token returns a safe validation error")
    void t2_blankGoogleIdTokenReturnsSafeValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("idToken"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    @DisplayName("t3 invalid Google id token returns a safe social login error")
    void t3_invalidGoogleIdTokenReturnsSafeSocialLoginError() throws Exception {
        String sensitiveToken = "invalid-sensitive-google-token";
        given(googleLoginService.login(sensitiveToken)).willThrow(new InvalidSocialTokenException());

        mockMvc.perform(post("/api/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"invalid-sensitive-google-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("Google 로그인에 실패했습니다."))
                .andExpect(content().string(not(containsString(sensitiveToken))))
                .andExpect(content().string(not(containsString("InvalidSocialTokenException"))));
    }
}
