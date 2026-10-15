package com.beandoche_osee_backend.domain.auth.provider.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoLoginStateStoreTest {
    private final Instant now = Instant.parse("2026-10-15T00:00:00Z");

    @Test
    @DisplayName("t1 state is unique and can only be consumed once")
    void t1_stateCanOnlyBeConsumedOnce() {
        var store = new KakaoLoginStateStore(Clock.fixed(now, ZoneOffset.UTC));
        var first = store.issue();
        assertThat(first.state()).isNotEqualTo(store.issue().state());
        store.consume(first.state(), first.binding());
        assertThatThrownBy(() -> store.consume(first.state(), first.binding())).isInstanceOf(KakaoLoginException.class);
    }

    @Test
    @DisplayName("t2 mismatched browser binding is rejected")
    void t2_mismatchedBrowserBindingIsRejected() {
        var store = new KakaoLoginStateStore(Clock.fixed(now, ZoneOffset.UTC));
        var state = store.issue();
        assertThatThrownBy(() -> store.consume(state.state(), "another-browser")).isInstanceOf(KakaoLoginException.class);
        assertThatThrownBy(() -> store.consume(null, state.binding())).isInstanceOf(KakaoLoginException.class);
    }

    @Test
    @DisplayName("t3 state expires after five minutes")
    void t3_stateExpiresAfterFiveMinutes() {
        var clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.BDDMockito.given(clock.instant()).willReturn(now);
        var store = new KakaoLoginStateStore(clock);
        var state = store.issue();
        org.mockito.BDDMockito.given(clock.instant()).willReturn(now.plusSeconds(300));
        assertThatThrownBy(() -> store.consume(state.state(), state.binding())).isInstanceOf(KakaoLoginException.class);
    }
}
