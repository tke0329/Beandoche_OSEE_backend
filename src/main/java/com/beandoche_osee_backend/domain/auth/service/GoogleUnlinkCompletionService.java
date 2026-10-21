package com.beandoche_osee_backend.domain.auth.service;

import java.time.Clock;
import com.beandoche_osee_backend.domain.auth.entity.UserStatus;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.InvalidAccessTokenException;
import com.beandoche_osee_backend.domain.profile.ProfileFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleUnlinkCompletionService {
    private final AccessTokenProvider access;
    private final UserRepository users;
    private final SocialUnlinkTasks tasks;
    private final Clock clock;
    public GoogleUnlinkCompletionService(AccessTokenProvider access, UserRepository users, SocialUnlinkTasks tasks, Clock clock) {
        this.access = access; this.users = users; this.tasks = tasks; this.clock = clock;
    }

    // The old signed token is accepted only to acknowledge this user's post-withdrawal browser action.
    // It never grants access to personal APIs or issues a new session.
    @Transactional
    public void confirm(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw denied();
        try {
            var claims = access.parse(authorization.substring(7));
            var user = users.findLocked(claims.userId()).orElseThrow(this::denied);
            if (user.getStatus() != UserStatus.WITHDRAWN) throw denied();
            tasks.confirmGoogleCompletion(user.getId(), clock.instant());
        } catch (InvalidAccessTokenException failure) {
            throw denied();
        }
    }

    private ProfileFailure denied() { return new ProfileFailure("INVALID_ACCESS_TOKEN", "연결 해제 완료 상태를 확인하지 못했습니다.", 401); }
}
