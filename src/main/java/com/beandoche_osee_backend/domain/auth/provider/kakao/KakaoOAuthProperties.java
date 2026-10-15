package com.beandoche_osee_backend.domain.auth.provider.kakao;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.kakao")
public record KakaoOAuthProperties(String restApiKey, String clientSecret, String redirectUri, String frontendOrigin) {
    public void requireConfigured() {
        if (restApiKey == null || restApiKey.isBlank() || restApiKey.startsWith("replace-")) {
            throw new KakaoLoginException("KAKAO_NOT_CONFIGURED");
        }
        if (clientSecret == null || clientSecret.isBlank() || clientSecret.startsWith("replace-")) {
            throw new KakaoLoginException("KAKAO_NOT_CONFIGURED");
        }
        try {
            URI callback = URI.create(redirectUri);
            URI frontend = URI.create(frontendOrigin);
            if (!safeUri(callback) || !safeUri(frontend) ||
                    !"/api/auth/social/kakao/callback".equals(callback.getPath()) ||
                    (frontend.getPath() != null && !frontend.getPath().isEmpty())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new KakaoLoginException("KAKAO_NOT_CONFIGURED");
        }
    }

    private boolean safeUri(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null &&
                ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) &&
                        ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))));
    }
}
