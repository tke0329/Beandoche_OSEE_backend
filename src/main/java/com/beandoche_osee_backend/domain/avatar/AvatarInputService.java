package com.beandoche_osee_backend.domain.avatar;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.avatar.AvatarInputDtos.*;
import com.beandoche_osee_backend.domain.avatar.AvatarInputStore.Body;
import com.beandoche_osee_backend.domain.avatar.AvatarInputStore.Generation;

@Service
public class AvatarInputService {
    private final UserRepository users;
    private final AvatarInputStore store;
    private final Clock clock;
    private final Validator validator;

    public AvatarInputService(UserRepository users, AvatarInputStore store, Clock clock, Validator validator) {
        this.users = users;
        this.store = store;
        this.clock = clock;
        this.validator = validator;
    }

    @Transactional
    public BodyView saveBody(long userId, BodyRequest request) {
        lockActiveUser(userId);
        validate(request, "INVALID_BODY_MEASUREMENT");
        var previous = store.body(userId);
        var measurements = request.measurements();
        var gender = request.normalizedGender();
        if (previous.isPresent() && Objects.equals(previous.get().gender(), gender)
                && previous.get().measurements().equals(measurements)) {
            return bodyView(userId, previous.get());
        }
        long version = previous.map(body -> Math.addExact(body.version(), 1)).orElse(1L);
        var body = new Body(version, gender, measurements, clock.instant());
        store.saveBody(userId, body);
        UUID generationId = UUID.randomUUID();
        store.createGeneration(userId, generationId, body);
        return new BodyView(version, gender, measurements, generationId, "PENDING", body.updatedAt());
    }

    @Transactional(readOnly = true)
    public BodyView getBody(long userId) {
        requireActiveUser(userId);
        return bodyView(userId, store.body(userId).orElseThrow(() ->
                new AvatarFailure("BODY_PROFILE_REQUIRED", "신체정보를 먼저 등록해 주세요.", 404)));
    }

    @Transactional(readOnly = true)
    public AvatarView getAvatar(long userId) {
        requireActiveUser(userId);
        return avatarView(userId);
    }

    @Transactional
    public AvatarView saveAppearance(long userId, AppearanceRequest request) {
        lockActiveUser(userId);
        validate(request, "INVALID_AVATAR_APPEARANCE");
        store.saveAppearance(userId, request.skinColorHex().toUpperCase(Locale.ROOT), clock.instant());
        return avatarView(userId);
    }

    @Transactional(readOnly = true)
    public ModelingInput exportInput(long userId, UUID generationId) {
        requireActiveUser(userId);
        Generation generation = store.generation(userId, generationId).orElseThrow(() ->
                new AvatarFailure("AVATAR_INPUT_NOT_FOUND", "아바타 입력 정보를 찾을 수 없습니다.", 404));
        return new ModelingInput(generation.schemaVersion(), generation.id(), generation.bodyVersion(),
                "mm", "kg", generation.gender(), generation.measurements());
    }

    @Transactional
    public void deleteBody(long userId) {
        lockActiveUser(userId);
        store.deleteBody(userId);
    }

    private BodyView bodyView(long userId, Body body) {
        Generation generation = store.latestGeneration(userId).filter(g -> g.bodyVersion() == body.version())
                .orElseThrow(() -> new AvatarFailure("AVATAR_INPUT_UNAVAILABLE", "입력 정보를 다시 확인해 주세요.", 409));
        return new BodyView(body.version(), body.gender(), body.measurements(), generation.id(), generation.status(), body.updatedAt());
    }

    private AvatarView avatarView(long userId) {
        var generation = store.latestGeneration(userId);
        var appearance = store.appearance(userId);
        return new AvatarView(generation.isPresent(), generation.map(Generation::id).orElse(null),
                generation.map(Generation::bodyVersion).orElse(null), generation.map(Generation::status).orElse(null),
                appearance.map(AvatarInputStore.Appearance::color).orElse(null),
                appearance.map(AvatarInputStore.Appearance::version).orElse(0L));
    }

    private void lockActiveUser(long userId) {
        users.findLocked(userId).filter(user -> user.isActive()).orElseThrow(AvatarInputService::denied);
    }

    private void validate(Object request, String code) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new AvatarFailure(code, "입력 형식과 단위를 확인해 주세요.", 400);
        }
    }

    private void requireActiveUser(long userId) {
        users.findById(userId).filter(user -> user.isActive()).orElseThrow(AvatarInputService::denied);
    }

    private static AvatarFailure denied() {
        return new AvatarFailure("RESOURCE_ACCESS_DENIED", "요청한 정보에 접근할 수 없습니다.", 403);
    }
}
