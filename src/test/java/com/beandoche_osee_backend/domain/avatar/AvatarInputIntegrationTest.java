package com.beandoche_osee_backend.domain.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdTokenVerifier;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdentity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class AvatarInputIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private GoogleIdTokenVerifier google;

    private String login(String subject) throws Exception {
        given(google.verify(subject)).willReturn(new GoogleIdentity(subject));
        var response = mvc.perform(post("/api/auth/web/google").header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + subject + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.accessToken");
    }

    private String body(int waist) {
        return """
                {"gender":"MALE","heightMm":1750,"weightKg":70,
                 "chestCircumferenceMm":950,"waistCircumferenceMm":%d,
                 "hipCircumferenceMm":980,"thighCircumferenceMm":550,"inseamMm":800}
                """.formatted(waist);
    }

    private String save(String token, int waist) throws Exception {
        return mvc.perform(put("/api/users/me/body-profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(waist)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test @DisplayName("t1 신체정보와 피부색 API는 인증이 필요하다")
    void t1_authenticationIsRequired() throws Exception {
        mvc.perform(get("/api/users/me/body-profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/avatar")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me/body-profile").contentType(MediaType.APPLICATION_JSON).content(body(800)))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("t2 신체정보 저장 후 중립 JSON을 본인만 내보낸다")
    void t2_bodyIsSavedAndExportedAsPrivateJson() throws Exception {
        String token = login("avatar-create");
        String saved = save(token, 800);
        String id = JsonPath.read(saved, "$.generationId");
        assertThat((Number) JsonPath.read(saved, "$.bodyVersion")).isEqualTo(1);
        mvc.perform(get("/api/users/me/body-profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.measurements.heightMm").value(1750));
        var exported = mvc.perform(get("/api/users/me/avatar/generations/" + id + "/input.json")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"avatar-input-" + id + ".json\""))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.lengthUnit").value("mm"))
                .andExpect(jsonPath("$.weightUnit").value("kg"))
                .andExpect(jsonPath("$.measurements.waistCircumferenceMm").value(800))
                .andExpect(jsonPath("$.userId").doesNotExist()).andReturn().getResponse();
        assertThat(exported.getContentAsString()).doesNotContain(token, "accessToken", "refreshToken");
        mvc.perform(get("/api/users/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.model").doesNotExist());
    }

    @Test @DisplayName("t3 같은 입력은 중복 생성하지 않고 수정 전 스냅샷을 보존한다")
    void t3_repeatedInputIsIdempotentAndHistoryIsImmutable() throws Exception {
        String token = login("avatar-version");
        String first = save(token, 800);
        String firstId = JsonPath.read(first, "$.generationId");
        assertThat((String) JsonPath.read(save(token, 800), "$.generationId")).isEqualTo(firstId);
        String second = save(token, 810);
        assertThat((Number) JsonPath.read(second, "$.bodyVersion")).isEqualTo(2);
        assertThat((String) JsonPath.read(second, "$.generationId")).isNotEqualTo(firstId);
        mvc.perform(get("/api/users/me/avatar/generations/" + firstId + "/input.json")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.measurements.waistCircumferenceMm").value(800));
    }

    @Test @DisplayName("t4 누락과 음수 및 소수 mm 입력을 거부한다")
    void t4_invalidMeasurementsAreRejectedWithoutSaving() throws Exception {
        String token = login("avatar-validation");
        for (String invalid : new String[] {"{}", body(0), body(-1), body(800).replace("1750", "1750.5"),
                body(800).replace("\"weightKg\":70", "\"weightKg\":70.1234")}) {
            mvc.perform(put("/api/users/me/body-profile").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        }
        mvc.perform(get("/api/users/me/body-profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("t5 임의 RGB 피부색 수정은 체형 요청을 재생성하지 않는다")
    void t5_colorChangesDoNotRegenerateTheBody() throws Exception {
        String token = login("avatar-color");
        String id = JsonPath.read(save(token, 800), "$.generationId");
        mvc.perform(patch("/api/users/me/avatar/appearance").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skinColorHex\":\"#00ff00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skinColorHex").value("#00FF00"))
                .andExpect(jsonPath("$.generationId").value(id));
        mvc.perform(patch("/api/users/me/avatar/appearance").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skinColorHex\":\"javascript:bad\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skinColorHex").value("#00FF00"))
                .andExpect(jsonPath("$.generationId").value(id)).andExpect(jsonPath("$.bodyVersion").value(1));
    }

    @Test @DisplayName("t6 타인의 스냅샷 접근과 삭제는 거부한다")
    void t6_otherUsersCannotReadOrDeleteSnapshots() throws Exception {
        String owner = login("avatar-owner");
        String id = JsonPath.read(save(owner, 800), "$.generationId");
        String other = login("avatar-other");
        mvc.perform(get("/api/users/me/avatar/generations/" + id + "/input.json")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AVATAR_INPUT_NOT_FOUND"));
        mvc.perform(delete("/api/users/me/body-profile").header("Authorization", "Bearer " + other))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/body-profile").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("t7 신체정보 삭제는 과거 입력도 제거하며 반복 삭제는 안전하다")
    void t7_bodyDeletionRemovesSnapshotsAndIsIdempotent() throws Exception {
        String token = login("avatar-delete");
        String id = JsonPath.read(save(token, 800), "$.generationId");
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/users/me/body-profile").header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/users/me/avatar/generations/" + id + "/input.json")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bodyRegistered").value(false));
    }

    @Test @DisplayName("t8 회원탈퇴 시 신체정보와 아바타 개인 데이터를 삭제한다")
    void t8_withdrawalRemovesBodyAndAvatarData() throws Exception {
        String token = login("avatar-withdraw");
        String id = JsonPath.read(save(token, 800), "$.generationId");
        mvc.perform(patch("/api/users/me/avatar/appearance").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"skinColorHex\":\"#123456\"}"))
                .andExpect(status().isOk());
        Long userId = jdbc.queryForObject("select user_id from avatar_generation_request where id=?::uuid", Long.class, id);
        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                        .header("Origin", "http://localhost:5173")).andExpect(status().isOk());
        for (String table : new String[] {"body_profile", "avatar_profile", "avatar_generation_request"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Long.class, userId)).isZero();
        }
        mvc.perform(get("/api/users/me/avatar").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("t9 DB는 비정상 치수와 피부색 및 결과 없는 완료를 차단한다")
    void t9_databaseConstraintsRejectInvalidData() throws Exception {
        String token = login("avatar-db");
        String id = JsonPath.read(save(token, 800), "$.generationId");
        Long userId = jdbc.queryForObject("select user_id from avatar_generation_request where id=?::uuid", Long.class, id);
        assertThatThrownBy(() -> jdbc.update("update body_profile set height_mm=0 where user_id=?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update avatar_generation_request set status='COMPLETED' where id=?::uuid", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into avatar_profile(user_id,skin_color_hex,appearance_version,updated_at) values (?,'bad',1,now())", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @DisplayName("t10 동시 동일 요청은 하나의 입력 버전만 생성한다")
    void t10_concurrentIdenticalRequestsCreateOneVersion() throws Exception {
        String token = login("avatar-concurrent");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> operation = () -> {
                start.await();
                return save(token, 800);
            };
            var first = executor.submit(operation);
            var second = executor.submit(operation);
            start.countDown();
            String firstId = JsonPath.read(first.get(15, java.util.concurrent.TimeUnit.SECONDS), "$.generationId");
            String secondId = JsonPath.read(second.get(15, java.util.concurrent.TimeUnit.SECONDS), "$.generationId");
            assertThat(firstId).isEqualTo(secondId);
        }
    }

    @Test @DisplayName("t11 선택 성별 누락과 동일 피부색 재전송을 안전하게 처리한다")
    void t11_optionalGenderAndRepeatedColorAreSupported() throws Exception {
        String token = login("avatar-optional");
        mvc.perform(put("/api/users/me/body-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(800).replace("\"gender\":\"MALE\",", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gender").isEmpty());
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/users/me/avatar/appearance").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"skinColorHex\":\"#FF00FF\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.appearanceVersion").value(1));
        }
    }

    @Test @DisplayName("t12 검증 실패의 신체 수치를 중복 예외 로그에 남기지 않는다")
    @ExtendWith(OutputCaptureExtension.class)
    void t12_validationLogsDoNotExposeRejectedMeasurements(CapturedOutput output) throws Exception {
        String token = login("avatar-log-privacy");
        var response = mvc.perform(put("/api/users/me/body-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(800).replace("1750", "1750.5")))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(output.getAll()).doesNotContain("rejected value [1750.5]");
        assertThat(response.getContentAsString()).doesNotContain("1750.5");
    }

    @Test @DisplayName("t13 지원하지 않는 본문 형식도 일관된 오류 응답을 반환한다")
    void t13_unsupportedMediaTypeUsesTheApiErrorContract() throws Exception {
        String token = login("avatar-content-type");
        mvc.perform(put("/api/users/me/body-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN).content(body(800)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
