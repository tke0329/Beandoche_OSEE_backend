package com.beandoche_osee_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.repository.SocialAccountRepository;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class BeandocheOseeBackendApplicationTests {
    @MockitoBean
    private com.beandoche_osee_backend.domain.avatar.AvatarInputStore avatarInputStore;
    @MockitoBean
    private com.beandoche_osee_backend.domain.auth.service.SocialUnlinkTasks socialUnlinkTasks;

    @MockitoBean
    private com.beandoche_osee_backend.domain.profile.UserProfileRepository userProfileRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SocialAccountRepository socialAccountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("t1 application context loads without infrastructure")
    void t1_contextLoadsWithoutInfrastructure() {
    }

}
