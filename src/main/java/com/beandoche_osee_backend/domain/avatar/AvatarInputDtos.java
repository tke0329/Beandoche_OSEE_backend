package com.beandoche_osee_backend.domain.avatar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class AvatarInputDtos {
    private AvatarInputDtos() { }

    public record BodyRequest(
            @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,31}") String gender,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal heightMm,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal chestCircumferenceMm,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal waistCircumferenceMm,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal hipCircumferenceMm,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal thighCircumferenceMm,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal inseamMm) {
        public String normalizedGender() {
            return gender == null ? null : gender.toUpperCase(Locale.ROOT);
        }

        public Measurements measurements() {
            return new Measurements(heightMm.intValueExact(), weightKg.stripTrailingZeros(),
                    chestCircumferenceMm.intValueExact(), waistCircumferenceMm.intValueExact(),
                    hipCircumferenceMm.intValueExact(), thighCircumferenceMm.intValueExact(), inseamMm.intValueExact());
        }
    }

    public record AppearanceRequest(@NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String skinColorHex) { }

    public record Measurements(int heightMm, BigDecimal weightKg, int chestCircumferenceMm,
            int waistCircumferenceMm, int hipCircumferenceMm, int thighCircumferenceMm, int inseamMm) { }

    public record BodyView(long bodyVersion, String gender, Measurements measurements,
            UUID generationId, String generationStatus, Instant updatedAt) { }

    public record AvatarView(boolean bodyRegistered, UUID generationId, Long bodyVersion,
            String status, String skinColorHex, long appearanceVersion) { }

    public record ModelingInput(int schemaVersion, UUID generationId, long bodyVersion,
            String lengthUnit, String weightUnit, String gender, Measurements measurements) { }
}
