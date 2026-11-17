package com.beandoche_osee_backend.domain.garment.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeasurementValidatorTest {
    private final MeasurementValidator validator = new MeasurementValidator();

    @Test
    @DisplayName("t1 필수 실측이 허용 범위 안이면 검증을 통과한다")
    void t1_acceptsValidMeasurements() {
        var result = validator.validate(List.of(rule("100", "1000")), List.of(value("500")));
        assertThat(result.status()).isEqualTo("VALID");
        assertThat(result.measurementReady()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    @DisplayName("t2 필수 누락과 비정상 값의 항목을 반환한다")
    void t2_reportsMissingAndNonPositive() {
        assertThat(validator.validate(List.of(rule("100", "1000")), List.of()).issues())
                .extracting(ValidationIssue::code).contains("MISSING_REQUIRED");
        assertThat(validator.validate(List.of(rule("100", "1000")), List.of(value("0"))).issues())
                .extracting(ValidationIssue::code).contains("NON_POSITIVE", "OUT_OF_RANGE");
    }

    @Test
    @DisplayName("t3 허용 범위가 없으면 임의로 통과시키지 않는다")
    void t3_requiresConfiguredRange() {
        var result = validator.validate(List.of(rule(null, null)), List.of(value("500")));
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.measurementReady()).isFalse();
        assertThat(result.issues()).extracting(ValidationIssue::code).contains("RANGE_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("t4 카테고리 규칙이 없거나 허용하지 않은 항목이면 피팅 가능하지 않다")
    void t4_requiresCategoryRules() {
        var result = validator.validate(List.of(), List.of(value("500")));
        assertThat(result.measurementReady()).isFalse();
        assertThat(result.issues()).extracting(ValidationIssue::code)
                .contains("NO_MEASUREMENT_RULES", "NOT_ALLOWED");
    }

    @Test
    @DisplayName("t5 범위 경계값은 포함하고 범위 밖 값은 거부한다")
    void t5_checksInclusiveBounds() {
        for (String value : List.of("100", "1000")) {
            assertThat(validator.validate(List.of(rule("100", "1000")), List.of(value(value))).measurementReady()).isTrue();
        }
        assertThat(validator.validate(List.of(rule("100", "1000")), List.of(value("1001"))).issues())
                .extracting(ValidationIssue::code).contains("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("t6 실측이 하나도 없으면 선택 항목만 있어도 검증 완료로 표시하지 않는다")
    void t6_requiresActualMeasurements() {
        var optional = new MeasurementRule(1L, "CHEST_WIDTH", "가슴단면", "mm", MeasurementBasis.FLAT_WIDTH,
                "측정 방법", true, false, 1, true, new BigDecimal("100"), new BigDecimal("1000"));
        assertThat(validator.validate(List.of(optional), List.of()).measurementReady()).isFalse();
    }

    private MeasurementRule rule(String min, String max) {
        return new MeasurementRule(1L, "CHEST_WIDTH", "가슴단면", "mm", MeasurementBasis.FLAT_WIDTH,
                "겨드랑이 아래 단면", true, true, 1, true,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max));
    }

    private Measurement value(String value) {
        return new Measurement(1L, "CHEST_WIDTH", "가슴단면", "mm", MeasurementBasis.FLAT_WIDTH,
                new BigDecimal(value), MeasurementSource.DIRECT_MEASUREMENT, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
