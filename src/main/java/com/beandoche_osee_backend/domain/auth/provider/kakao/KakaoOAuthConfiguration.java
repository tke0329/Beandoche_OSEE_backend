package com.beandoche_osee_backend.domain.auth.provider.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthConfiguration {
    @Bean
    KakaoApiClient kakaoApiClient(KakaoOAuthProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new KakaoApiClient(properties,
                RestClient.builder().baseUrl("https://kauth.kakao.com").requestFactory(factory).build(),
                RestClient.builder().baseUrl("https://kapi.kakao.com").requestFactory(factory).build());
    }
}
