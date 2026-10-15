package com.beandoche_osee_backend.domain.auth.provider.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoApiClientTest {
    private final RestClient.Builder auth = RestClient.builder().baseUrl("https://kauth.kakao.com");
    private final RestClient.Builder user = RestClient.builder().baseUrl("https://kapi.kakao.com");
    private final MockRestServiceServer authServer = MockRestServiceServer.bindTo(auth).build();
    private final MockRestServiceServer userServer = MockRestServiceServer.bindTo(user).build();
    private final KakaoOAuthProperties properties = new KakaoOAuthProperties("rest-key", "client-secret", "http://localhost:8080/api/auth/social/kakao/callback", "http://localhost:5173");

    @Test
    @DisplayName("t1 authorization code is exchanged server-side and verified user ID is returned")
    void t1_codeIsExchangedAndUserIdVerified() {
        authServer.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        userServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));
        assertThat(new KakaoApiClient(properties, auth.build(), user.build()).verifyCode("code")).isEqualTo("12345");
        authServer.verify();
        userServer.verify();
    }

    @Test
    @DisplayName("t2 invalid provider response is rejected without exposing provider details")
    void t2_invalidProviderResponseIsRejected() {
        authServer.expect(anything()).andRespond(withBadRequest().body("sensitive-provider-response"));
        assertThatThrownBy(() -> new KakaoApiClient(properties, auth.build(), user.build()).verifyCode("code"))
                .isInstanceOf(KakaoLoginException.class).hasMessageNotContaining("sensitive");
    }

    @Test
    @DisplayName("t3 missing user ID is rejected")
    void t3_missingUserIdIsRejected() {
        authServer.expect(anything()).andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        userServer.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new KakaoApiClient(properties, auth.build(), user.build()).verifyCode("code"))
                .isInstanceOf(KakaoLoginException.class);
    }
}
