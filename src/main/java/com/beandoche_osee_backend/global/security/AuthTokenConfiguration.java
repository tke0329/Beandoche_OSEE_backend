package com.beandoche_osee_backend.global.security;

import java.security.SecureRandom;
import java.time.Clock;

import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.RefreshTokenGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthTokenProperties.class)
public class AuthTokenConfiguration {

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom authSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    AccessTokenProvider accessTokenProvider(AuthTokenProperties properties, Clock authClock) {
        return new AccessTokenProvider(
                properties.jwtSecret(),
                properties.accessTokenTtl(),
                authClock);
    }

    @Bean
    RefreshTokenGenerator refreshTokenGenerator(
            AuthTokenProperties properties,
            SecureRandom authSecureRandom,
            Clock authClock) {
        return new RefreshTokenGenerator(
                authSecureRandom,
                authClock,
                properties.sessionTtl());
    }
}
