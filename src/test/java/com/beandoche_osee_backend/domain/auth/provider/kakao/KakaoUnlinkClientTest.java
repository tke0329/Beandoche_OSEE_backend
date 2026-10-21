package com.beandoche_osee_backend.domain.auth.provider.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {
    @Test @DisplayName("t1 missing admin key defers unlink without an external request")
    void t1_missingKeyDefersUnlink() {
        var builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        assertThat(new KakaoUnlinkClient("", builder.build()).unlink("123"))
                .isEqualTo(KakaoUnlinkClient.Outcome.CONFIG_REQUIRED);
        server.verify();
    }

    @Test @DisplayName("t2 unlink sends a server-only admin authorization and verifies returned identity")
    void t2_unlinkVerifiesIdentity() {
        var builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andExpect(header("Authorization", "KakaoAK test-admin"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("target_id=123")))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        assertThat(new KakaoUnlinkClient("test-admin", builder.build()).unlink("123"))
                .isEqualTo(KakaoUnlinkClient.Outcome.COMPLETED);
        server.verify();
    }

    @Test @DisplayName("t3 provider outage becomes a retry without exposing its response")
    void t3_providerOutageRetries() {
        var builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withServerError().body("private-provider-message"));
        assertThat(new KakaoUnlinkClient("test-admin", builder.build()).unlink("123"))
                .isEqualTo(KakaoUnlinkClient.Outcome.RETRY);
        server.verify();
    }

    @Test @DisplayName("t4 invalid admin permissions defer processing for configuration correction")
    void t4_invalidPermissionRequiresConfiguration() {
        var builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(new KakaoUnlinkClient("test-admin", builder.build()).unlink("123"))
                .isEqualTo(KakaoUnlinkClient.Outcome.CONFIG_REQUIRED);
        server.verify();
    }
}
