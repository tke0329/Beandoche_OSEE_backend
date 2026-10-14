package com.beandoche_osee_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdTokenVerifier;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleLoginApplicationServiceTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private SocialLoginTransactionService socialLoginTransactionService;

    @InjectMocks
    private GoogleLoginApplicationService service;

    @Test
    @DisplayName("t1 verified Google subject is used for the social login transaction")
    void t1_verifiedGoogleSubjectIsUsedForSocialLoginTransaction() {
        SocialLoginResult expected = new SocialLoginResult(
                42L,
                true,
                "access-token",
                Instant.parse("2026-10-08T00:15:00Z"),
                "refresh-token",
                Instant.parse("2026-10-10T00:00:00Z"));
        given(googleIdTokenVerifier.verify("google-id-token"))
                .willReturn(new GoogleIdentity("google-subject-1"));
        given(socialLoginTransactionService.login(SocialProvider.GOOGLE, "google-subject-1"))
                .willReturn(expected);

        SocialLoginResult result = service.login("google-id-token");

        assertThat(result).isEqualTo(expected);
        verify(socialLoginTransactionService).login(SocialProvider.GOOGLE, "google-subject-1");
    }
}
