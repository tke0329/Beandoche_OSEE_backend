package com.beandoche_osee_backend.domain.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.avatar.AvatarInputDtos.*;
import com.beandoche_osee_backend.domain.avatar.AvatarInputStore.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarInputServiceTest {
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
    @Mock private UserRepository users;
    @Mock private AvatarInputStore store;

    private AvatarInputService service() {
        return new AvatarInputService(users, store, Clock.fixed(now, ZoneOffset.UTC), validation.getValidator());
    }

    @AfterEach
    void closeValidationFactory() {
        validation.close();
    }

    private BodyRequest request(int height) {
        return new BodyRequest("male", BigDecimal.valueOf(height), new BigDecimal("70.000"),
                new BigDecimal("950"), new BigDecimal("800"), new BigDecimal("980"),
                new BigDecimal("550"), new BigDecimal("800"));
    }

    @Test @DisplayName("t1 비활성 사용자는 저장소에 접근하지 못한다")
    void t1_inactiveUserCannotWrite() {
        given(users.findLocked(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service().saveBody(7L, request(1750))).isInstanceOf(AvatarFailure.class);
        verifyNoInteractions(store);
    }

    @Test @DisplayName("t2 정규화 후 같은 입력이면 기존 버전과 요청을 재사용한다")
    void t2_identicalInputReusesExistingVersion() {
        given(users.findLocked(7L)).willReturn(Optional.of(User.active(now)));
        var request = request(1750);
        var id = UUID.randomUUID();
        given(store.body(7L)).willReturn(Optional.of(new Body(3, "MALE", request.measurements(), now)));
        given(store.latestGeneration(7L)).willReturn(Optional.of(new Generation(id, 3, 1, "PENDING", "MALE", request.measurements())));
        var view = service().saveBody(7L, request);
        assertThat(view.bodyVersion()).isEqualTo(3);
        assertThat(view.generationId()).isEqualTo(id);
        verify(store, never()).saveBody(anyLong(), any());
        verify(store, never()).createGeneration(anyLong(), any(), any());
    }

    @Test @DisplayName("t3 변경된 입력은 다음 버전의 대기 요청으로 저장한다")
    void t3_changedInputCreatesNextPendingVersion() {
        given(users.findLocked(7L)).willReturn(Optional.of(User.active(now)));
        given(store.body(7L)).willReturn(Optional.of(new Body(3, "MALE", request(1750).measurements(), now)));
        var view = service().saveBody(7L, request(1760));
        assertThat(view.bodyVersion()).isEqualTo(4);
        assertThat(view.generationStatus()).isEqualTo("PENDING");
        verify(store).createGeneration(eq(7L), eq(view.generationId()), argThat(body -> body.version() == 4));
    }

    @Test @DisplayName("t4 피부색은 대문자로 정규화하며 체형을 재생성하지 않는다")
    void t4_colorChangeDoesNotCreateGeneration() {
        given(users.findLocked(7L)).willReturn(Optional.of(User.active(now)));
        service().saveAppearance(7L, new AppearanceRequest("#abcdef"));
        verify(store).saveAppearance(7L, "#ABCDEF", now);
        verify(store, never()).createGeneration(anyLong(), any(), any());
    }

    @Test @DisplayName("t5 스냅샷 조회에는 현재 사용자 ID를 함께 제한한다")
    void t5_exportAlwaysUsesAuthenticatedOwner() {
        given(users.findById(7L)).willReturn(Optional.of(User.active(now)));
        UUID id = UUID.randomUUID();
        given(store.generation(7L, id)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service().exportInput(7L, id)).isInstanceOfSatisfying(AvatarFailure.class,
                failure -> assertThat(failure.code()).isEqualTo("AVATAR_INPUT_NOT_FOUND"));
    }

    @Test @DisplayName("t6 서비스 직접 호출도 잘못된 신체정보를 저장하지 않는다")
    void t6_directServiceCallsValidateMeasurements() {
        given(users.findLocked(7L)).willReturn(Optional.of(User.active(now)));
        assertThatThrownBy(() -> service().saveBody(7L, request(-1))).isInstanceOf(AvatarFailure.class);
        verifyNoInteractions(store);
    }
}
