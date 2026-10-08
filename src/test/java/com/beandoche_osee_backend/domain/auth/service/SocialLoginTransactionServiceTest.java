package com.beandoche_osee_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import com.beandoche_osee_backend.domain.auth.entity.SocialAccount;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.repository.SocialAccountRepository;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenClaims;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.IssuedRefreshToken;
import com.beandoche_osee_backend.domain.auth.token.RefreshTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialLoginTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final Instant ACCESS_EXPIRES_AT = NOW.plusSeconds(900);
    private static final Instant SESSION_EXPIRES_AT = NOW.plusSeconds(172_800);

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenProvider accessTokenProvider;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    private SocialLoginTransactionService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginTransactionService(
                userRepository,
                socialAccountRepository,
                refreshTokenRepository,
                accessTokenProvider,
                refreshTokenGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("t1 first Google login creates one user and one social account")
    void t1_firstGoogleLoginCreatesOneUserAndOneSocialAccount() {
        User savedUser = activeUser(42L);
        given(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-subject-1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        givenTokens(savedUser);

        SocialLoginResult result = service.login(SocialProvider.GOOGLE, "google-subject-1");

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.newUser()).isTrue();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isActive()).isTrue();
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("google-subject-1");
    }

    @Test
    @DisplayName("t2 repeated Google login reuses the existing user")
    void t2_repeatedGoogleLoginReusesExistingUser() {
        User existingUser = activeUser(42L);
        SocialAccount account = SocialAccount.link(
                existingUser,
                SocialProvider.GOOGLE,
                "google-subject-1",
                NOW);
        given(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-subject-1")).willReturn(Optional.of(account));
        givenTokens(existingUser);

        SocialLoginResult result = service.login(SocialProvider.GOOGLE, "google-subject-1");

        assertThat(result.newUser()).isFalse();
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("t3 suspended user cannot sign in with Google")
    void t3_suspendedUserCannotSignInWithGoogle() {
        User suspendedUser = User.active(NOW);
        suspendedUser.suspend();
        SocialAccount account = SocialAccount.link(
                suspendedUser,
                SocialProvider.GOOGLE,
                "google-subject-1",
                NOW);
        given(socialAccountRepository.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-subject-1")).willReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(SocialProvider.GOOGLE, "google-subject-1"))
                .isExactlyInstanceOf(SocialLoginNotAllowedException.class)
                .hasMessage(null);
        verify(accessTokenProvider, never()).issue(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    private void givenTokens(User user) {
        given(accessTokenProvider.issue(42L)).willReturn("access-token");
        given(accessTokenProvider.parse("access-token"))
                .willReturn(new AccessTokenClaims(42L, NOW, ACCESS_EXPIRES_AT));
        given(refreshTokenGenerator.issue()).willReturn(new IssuedRefreshToken(
                "refresh-token",
                "a".repeat(64),
                SESSION_EXPIRES_AT,
                SESSION_EXPIRES_AT));
        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private User activeUser(Long id) {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(id);
        given(user.isActive()).willReturn(true);
        return user;
    }
}
