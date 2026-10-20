package com.beandoche_osee_backend.domain.auth.service;

import java.time.Clock;
import java.util.Optional;
import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import com.beandoche_osee_backend.domain.auth.repository.RefreshTokenRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.RefreshTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebSessionService {
    private final RefreshTokenRepository tokens;
    private final RefreshTokenGenerator generator;
    private final AccessTokenProvider access;
    private final Clock clock;

    public WebSessionService(RefreshTokenRepository tokens, RefreshTokenGenerator generator, AccessTokenProvider access, Clock clock) {
        this.tokens = tokens;
        this.generator = generator;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Optional<SocialLoginResult> rotate(String raw) {
        var found = find(raw);
        if (found.isEmpty()) return Optional.empty();
        var token = found.get();
        var now = clock.instant();
        if (!token.isUsableAt(now) || !token.getUser().isActive()) return Optional.empty();
        token.revoke(now);
        var issued = generator.rotate(token.getAbsoluteExpiresAt());
        tokens.save(RefreshToken.issue(token.getUser(), issued.tokenHash(), issued.absoluteExpiresAt(), issued.expiresAt(), now));
        String jwt = access.issue(token.getUser().getId(), issued.absoluteExpiresAt());
        return Optional.of(new SocialLoginResult(token.getUser().getId(), false, jwt, access.parse(jwt).expiresAt(), issued.rawToken(), issued.absoluteExpiresAt()));
    }

    @Transactional
    public void logout(String raw) {
        find(raw).filter(token -> token.getRevokedAt() == null).ifPresent(token -> token.revoke(clock.instant()));
    }

    private Optional<RefreshToken> find(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) return Optional.empty();
        return tokens.findLockedByTokenHash(generator.hash(raw));
    }
}
