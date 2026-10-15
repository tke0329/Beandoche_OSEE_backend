package com.beandoche_osee_backend.domain.auth.provider.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class KakaoApiClient {
    private final KakaoOAuthProperties properties;
    private final RestClient auth;
    private final RestClient user;

    public KakaoApiClient(KakaoOAuthProperties properties, RestClient auth, RestClient user) {
        this.properties = properties;
        this.auth = auth;
        this.user = user;
    }

    public String verifyCode(String code) {
        properties.requireConfigured();
        if (code == null || code.isBlank() || code.length() > 2048) throw new KakaoLoginException("KAKAO_LOGIN_FAILED");
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.restApiKey());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        try {
            TokenResponse token = auth.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(TokenResponse.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new KakaoLoginException("KAKAO_LOGIN_FAILED");
            }
            UserResponse identity = user.get().uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(token.accessToken())).retrieve().body(UserResponse.class);
            if (identity == null || identity.id() == null || identity.id() <= 0) throw new KakaoLoginException("KAKAO_LOGIN_FAILED");
            return identity.id().toString();
        } catch (HttpClientErrorException exception) {
            throw new KakaoLoginException("KAKAO_LOGIN_FAILED");
        } catch (RestClientException exception) {
            throw new KakaoLoginException("KAKAO_UNAVAILABLE");
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) { }
    private record UserResponse(Long id) { }
}
