package com.beandoche_osee_backend.domain.auth.service;

import java.time.Clock;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.repository.SocialAccountRepository;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.profile.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWithdrawalService {
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final SocialAccountRepository accounts;
    private final UserProfileRepository profiles;
    private final SocialUnlinkTasks tasks;
    private final com.beandoche_osee_backend.domain.avatar.AvatarInputStore avatarInputs;
    private final Clock clock;
    public UserWithdrawalService(UserRepository users, RefreshTokenRepository tokens, SocialAccountRepository accounts,
            UserProfileRepository profiles, SocialUnlinkTasks tasks, Clock clock,
            com.beandoche_osee_backend.domain.avatar.AvatarInputStore avatarInputs) {
        this.users = users; this.tokens = tokens; this.accounts = accounts; this.profiles = profiles; this.tasks = tasks; this.clock = clock;
        this.avatarInputs = avatarInputs;
    }

    @Transactional
    public Result withdraw(Long userId) {
        var user = users.findLocked(userId).filter(u -> u.isActive()).orElseThrow(SocialLoginNotAllowedException::new);
        var linked = accounts.findAllByUserId(userId);
        var now = clock.instant();
        for (var account : linked) tasks.enqueue(account, now);
        user.withdraw(now);
        tokens.revokeAll(userId, now);
        profiles.deleteById(userId);
        avatarInputs.deleteUserData(userId);
        accounts.deleteAll(linked);
        return new Result(true, linked.stream().anyMatch(a -> a.getProvider() == SocialProvider.GOOGLE),
                linked.stream().anyMatch(a -> a.getProvider() == SocialProvider.KAKAO),
                linked.stream().filter(a -> a.getProvider() == SocialProvider.GOOGLE).map(a -> a.getProviderUserId()).toList());
    }

    public record Result(boolean withdrawn, boolean googleUnlinkRequired, boolean kakaoUnlinkPending, java.util.List<String> googleSubjects) { }
}
