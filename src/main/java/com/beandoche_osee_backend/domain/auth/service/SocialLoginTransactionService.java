package com.beandoche_osee_backend.domain.auth.service;

import java.time.Clock;
import java.time.Instant;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLoginTransactionService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final Clock clock;
    private final SocialUnlinkTasks unlinkTasks;

    public SocialLoginTransactionService(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenProvider accessTokenProvider,
            RefreshTokenGenerator refreshTokenGenerator,
            Clock clock, SocialUnlinkTasks unlinkTasks) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.clock = clock;
        this.unlinkTasks = unlinkTasks;
    }

    @Transactional
    public SocialLoginResult login(SocialProvider provider, String providerUserId) {
        if (unlinkTasks.hasPending(provider, providerUserId)) throw new SocialLoginNotAllowedException();
        ResolvedUser resolvedUser = findOrCreateUser(provider, providerUserId);
        User user = resolvedUser.user();
        if (!user.isActive()) {
            throw new SocialLoginNotAllowedException();
        }

        String accessToken = accessTokenProvider.issue(user.getId());
        AccessTokenClaims accessClaims = accessTokenProvider.parse(accessToken);
        IssuedRefreshToken issuedRefreshToken = refreshTokenGenerator.issue();
        persistRefreshToken(user, issuedRefreshToken);

        return new SocialLoginResult(
                user.getId(),
                resolvedUser.newUser(),
                accessToken,
                accessClaims.expiresAt(),
                issuedRefreshToken.rawToken(),
                issuedRefreshToken.absoluteExpiresAt());
    }

    private ResolvedUser findOrCreateUser(SocialProvider provider, String providerUserId) {
        return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(account -> new ResolvedUser(userRepository.findLocked(account.getUser().getId()).orElseThrow(SocialLoginNotAllowedException::new), false))
                .orElseGet(() -> createUser(provider, providerUserId));
    }

    private ResolvedUser createUser(SocialProvider provider, String providerUserId) {
        Instant now = clock.instant();
        User user = userRepository.save(User.active(now));
        socialAccountRepository.save(SocialAccount.link(user, provider, providerUserId, now));
        return new ResolvedUser(user, true);
    }

    private void persistRefreshToken(User user, IssuedRefreshToken issuedToken) {
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                issuedToken.tokenHash(),
                issuedToken.absoluteExpiresAt(),
                issuedToken.expiresAt(),
                clock.instant()));
    }

    private record ResolvedUser(User user, boolean newUser) {
    }
}
