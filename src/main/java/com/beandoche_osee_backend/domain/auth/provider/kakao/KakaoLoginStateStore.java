package com.beandoche_osee_backend.domain.auth.provider.kakao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KakaoLoginStateStore {
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> pending = new HashMap<>();

    public KakaoLoginStateStore(Clock clock) { this.clock = clock; }

    public synchronized IssuedState issue() {
        pending.values().removeIf(entry -> !clock.instant().isBefore(entry.expiresAt()));
        if (pending.size() >= 2048) throw new KakaoLoginException("KAKAO_UNAVAILABLE");
        String state = randomValue();
        String binding = randomValue();
        pending.put(state, new Entry(binding, clock.instant().plusSeconds(300)));
        return new IssuedState(state, binding);
    }

    public synchronized void consume(String state, String binding) {
        Entry entry = state == null ? null : pending.get(state);
        if (entry == null || binding == null || !clock.instant().isBefore(entry.expiresAt()) ||
                !MessageDigest.isEqual(entry.binding().getBytes(StandardCharsets.UTF_8), binding.getBytes(StandardCharsets.UTF_8))) {
            throw new KakaoLoginException("KAKAO_STATE_INVALID");
        }
        pending.remove(state);
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedState(String state, String binding) { }
    private record Entry(String binding, Instant expiresAt) { }
}
