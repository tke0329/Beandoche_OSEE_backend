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
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NICKNAME_CHANGE_NOT_ALLOWED"));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        String other = JsonPath.read(login("other-profile-user").getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.nickname").isEmpty());
        mvc.perform(multipart("/api/users/me").param("nickname", "osee_user").with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", "Bearer " + other))
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
}
