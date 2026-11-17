package com.beandoche_osee_backend.domain.garment.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;
import static org.assertj.core.api.Assertions.assertThat;

class MaterialPolicyTest {
    private final MaterialPolicy policy = new MaterialPolicy();

    @Test
    @DisplayName("t1 확인된 혼용률은 합계가 정확히 100이어야 한다")
    void t1_requiresTotalOneHundred() {
        assertThat(policy.valid(MaterialStatus.KNOWN, List.of(share("60.5"), share("39.5")))).isTrue();
        assertThat(policy.valid(MaterialStatus.KNOWN, List.of(share("60")))).isFalse();
        assertThat(policy.valid(MaterialStatus.KNOWN, List.of())).isFalse();
    }

    @Test
    @DisplayName("t2 미확인 상태와 확인된 합계 100을 구분한다")
    void t2_allowsUnknownComposition() {
        assertThat(policy.valid(MaterialStatus.UNKNOWN, List.of())).isTrue();
        assertThat(policy.valid(MaterialStatus.UNKNOWN, List.of(share("60")))).isTrue();
    }

    @Test
    @DisplayName("t3 혼용률 개별 값은 양수이며 100 이하여야 한다")
    void t3_checksIndividualPercentages() {
        for (String value : List.of("0", "-1", "101")) {
            assertThat(policy.valid(MaterialStatus.UNKNOWN, List.of(share(value)))).isFalse();
        }
    }

    private MaterialShare share(String value) {
        return new MaterialShare(1L, "COTTON", "면", new BigDecimal(value));
    }
}
