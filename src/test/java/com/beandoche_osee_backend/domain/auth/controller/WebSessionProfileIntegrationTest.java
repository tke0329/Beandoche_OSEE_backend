package com.beandoche_osee_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdTokenVerifier;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdentity;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WebSessionProfileIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired private MockMvc mvc;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.beandoche_osee_backend.domain.auth.service.SocialUnlinkWorker unlinkWorker;
    @MockitoBean private com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoUnlinkClient kakaoUnlink;
    @MockitoBean private GoogleIdTokenVerifier google;

    private org.springframework.mock.web.MockHttpServletResponse login(String id) throws Exception {
        given(google.verify(id)).willReturn(new GoogleIdentity(id));
        return mvc.perform(post("/api/auth/web/google").header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + id + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    @Test @DisplayName("t1 cookie restores across requests and rotation preserves absolute expiry")
    void t1_cookieRestoresAndRotates() throws Exception {
        var login = login("session-user");
        var cookie = login.getCookie("osee_refresh");
        assertThat(login.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth");
        assertThat(login.getContentAsString()).doesNotContain("refreshToken", cookie.getValue());
        var restored = mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat((String)JsonPath.read(restored.getContentAsString(), "$.sessionExpiresAt"))
                .isEqualTo((String)JsonPath.read(login.getContentAsString(), "$.sessionExpiresAt"));
        assertThat(restored.getCookie("osee_refresh").getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/web/logout").header("Origin", "http://localhost:5173").cookie(restored.getCookie("osee_refresh"))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(restored.getCookie("osee_refresh"))).andExpect(status().isNoContent());
    }

    @Test @DisplayName("t2 cookie APIs reject untrusted or missing Origin")
    void t2_originIsRequired() throws Exception {
        mvc.perform(post("/api/auth/web/session")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/web/logout").header("Origin", "http://attacker.example")).andExpect(status().isForbidden());
    }

    @Test @DisplayName("t3 initial profile normalizes nickname and stores a validated image")
    void t3_initialProfileIsPrivateAndValidated() throws Exception {
        String access = JsonPath.read(login("profile-user").getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(jsonPath("$.profileCompleted").value(false));
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        mvc.perform(multipart("/api/users/me").file(new MockMultipartFile("image", "avatar.png", "image/png", bytes.toByteArray()))
                        .param("nickname", "  Osee_User  ").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Osee_User"))
                .andExpect(jsonPath("$.profileCompleted").value(true)).andExpect(jsonPath("$.imageBase64").isNotEmpty());
        mvc.perform(multipart("/api/users/me").param("nickname", "Different_Name").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nicknameNextChangeAt").isEmpty());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        String other = JsonPath.read(login("other-profile-user").getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.nickname").isEmpty());
        mvc.perform(multipart("/api/users/me").param("nickname", "different_name").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + other))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
    }

    @Test @DisplayName("t4 fake or oversized profile images are rejected without creating a profile")
    void t4_invalidImagesAreRejected() throws Exception {
        String access = JsonPath.read(login("invalid-image-user").getContentAsString(), "$.accessToken");
        mvc.perform(multipart("/api/users/me").file(new MockMultipartFile("image", "fake.png", "image/png", "not-an-image".getBytes()))
                        .param("nickname", "valid_name").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROFILE_IMAGE"));
        mvc.perform(multipart("/api/users/me").file(new MockMultipartFile("image", "big.png", "image/png", new byte[1048577]))
                        .param("nickname", "valid_name").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(jsonPath("$.profileCompleted").value(false));
    }

    @Test @DisplayName("t5 blank initial nickname is generated and duplicate nickname is rejected")
    void t5_blankInitialNicknameIsGenerated() throws Exception {
        String access = JsonPath.read(login("auto-nickname-user").getContentAsString(), "$.accessToken");
        var saved = mvc.perform(multipart("/api/users/me").param("nickname", "   ")
                        .with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileCompleted").value(true)).andReturn().getResponse();
        String nickname = JsonPath.read(saved.getContentAsString(), "$.nickname");
        assertThat(nickname).matches("OSEE_[0-9]{9}");
        mvc.perform(multipart("/api/users/me").param("nickname", "")
                        .with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        String other = JsonPath.read(login("duplicate-auto-user").getContentAsString(), "$.accessToken");
        mvc.perform(multipart("/api/users/me").param("nickname", nickname.toLowerCase())
                        .with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + other))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
    }

    @Test @DisplayName("t6 nickname checks reject invalid and prohibited terms on both preview and save")
    void t6_nicknamePolicyCannotBeBypassed() throws Exception {
        String access = JsonPath.read(login("policy-user").getContentAsString(), "$.accessToken");
        for (String nickname : new String[]{"FUCK", "s_e_x", "시발", "병신", "ＦｕＣＫ", "씨1발"}) {
            mvc.perform(get("/api/users/me/nickname-availability").param("nickname", nickname).header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.code").value("NICKNAME_NOT_ALLOWED"));
            mvc.perform(multipart("/api/users/me").param("nickname", nickname).with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NICKNAME_NOT_ALLOWED"));
        }
        mvc.perform(get("/api/users/me/nickname-availability").param("nickname", "a!").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        mvc.perform(multipart("/api/users/me").param("nickname", "Clean_name").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/nickname-availability").param("nickname", "Clean_name").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        String other = JsonPath.read(login("policy-other-user").getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/users/me/nickname-availability").param("nickname", "clean_NAME").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));
        mvc.perform(get("/api/users/me/nickname-availability").param("nickname", "Clean_name")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("t7 withdrawal removes own profile and social link and invalidates every session")
    void t7_withdrawalInvalidatesAllSessions() throws Exception {
        var first = login("withdraw-user");
        var second = login("withdraw-user");
        String access = JsonPath.read(first.getContentAsString(), "$.accessToken");
        Number userId = JsonPath.read(first.getContentAsString(), "$.userId");
        mvc.perform(multipart("/api/users/me").param("nickname", "To_withdraw").with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        var withdrawn = mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + access).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleUnlinkRequired").value(true)).andReturn().getResponse();
        assertThat(withdrawn.getCookie("osee_refresh").getMaxAge()).isZero();
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, userId)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select count(*) from user_profile where user_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from social_account where user_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and revoked_at is null", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select status from social_unlink_task where user_id=?", String.class, userId)).isEqualTo("USER_ACTION_REQUIRED");
        assertThat(jdbc.queryForObject("select provider_user_id from social_unlink_task where user_id=?", String.class, userId)).isNull();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(second.getCookie("osee_refresh")))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("t8 withdrawal requires authentication and trusted origin")
    void t8_withdrawalRequiresAuthenticationAndOrigin() throws Exception {
        mvc.perform(delete("/api/users/me").header("Origin", "http://localhost:5173")).andExpect(status().isUnauthorized());
        String access = JsonPath.read(login("withdraw-origin-user").getContentAsString(), "$.accessToken");
        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
    }

    @Test @DisplayName("t9 stale session responses must not clear a newer rotated cookie")
    void t9_staleSessionDoesNotClearCookie() throws Exception {
        var first = login("stale-cookie-user").getCookie("osee_refresh");
        mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(first)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/web/session").header("Origin", "http://localhost:5173").cookie(first))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test @DisplayName("t10 provider outage keeps withdrawal committed and unlink retry can complete")
    void t10_externalFailureDoesNotRollbackWithdrawal() throws Exception {
        var first = login("kakao-withdraw-user");
        String access = JsonPath.read(first.getContentAsString(), "$.accessToken");
        Number userId = JsonPath.read(first.getContentAsString(), "$.userId");
        jdbc.update("update social_account set provider='KAKAO',provider_user_id='qa-kakao-unlink' where user_id=?", userId);
        given(kakaoUnlink.unlink("qa-kakao-unlink")).willReturn(
                com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoUnlinkClient.Outcome.RETRY,
                com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoUnlinkClient.Outcome.COMPLETED);
        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + access).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kakaoUnlinkPending").value(true));
        unlinkWorker.processPending();
        assertThat(jdbc.queryForObject("select status from social_unlink_task where user_id=?", String.class, userId)).isEqualTo("RETRY");
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, userId)).isEqualTo("WITHDRAWN");
        jdbc.update("update social_unlink_task set next_attempt_at=now()-interval '1 second' where user_id=?", userId);
        unlinkWorker.processPending();
        assertThat(jdbc.queryForObject("select status from social_unlink_task where user_id=?", String.class, userId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select provider_user_id from social_unlink_task where user_id=?", String.class, userId)).isNull();
    }
}
