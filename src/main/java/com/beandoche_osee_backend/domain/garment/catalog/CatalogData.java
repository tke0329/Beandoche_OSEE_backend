package com.beandoche_osee_backend.domain.garment.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogData {
    private CatalogData() {
    }

    public enum MeasurementBasis { LENGTH, FLAT_WIDTH, CIRCUMFERENCE, WEIGHT }
    public enum MeasurementSource { BRAND_PROVIDED, DIRECT_MEASUREMENT, USER_PROVIDED, ESTIMATED }
    public enum MaterialStatus { UNKNOWN, KNOWN }

    public record Variant(long id, long garmentId, String colorName, String colorCode,
                          String colorHex, String thumbnailUrl, boolean active) {
    }

    public record Size(long id, long garmentVariantId, String sizeLabel, int sortOrder,
                       String sku, boolean available) {
    }

    public record VariantResponse(long id, String colorName, String colorCode, String colorHex,
                                  String thumbnailUrl, List<Size> sizes) {
    }

    public record MaterialShare(long id, String code, String name, BigDecimal percentage) {
    }

    public record Options(MaterialStatus materialStatus, List<MaterialShare> materials,
                          List<VariantResponse> variants, List<MeasurementRule> measurementDefinitions) {
    }

    public record MeasurementRule(long id, String code, String name, String defaultUnit,
                                  MeasurementBasis measurementBasis, String description,
                                  boolean active, boolean required, int displayOrder,
                                  boolean fitAnalysisEnabled, BigDecimal minValueMm, BigDecimal maxValueMm) {
    }

    public record Measurement(long definitionId, String code, String name, String unit,
                              MeasurementBasis measurementBasis, BigDecimal valueMm,
                              MeasurementSource source, Instant measuredAt) {
    }

    public record ValidationIssue(String code, String measurementCode, String message) {
    }

    public record Validation(String status, boolean measurementReady, List<ValidationIssue> issues) {
    }

    public record ModelSupport(String status, boolean available) {
        public static ModelSupport notProvided() {
            return new ModelSupport("NOT_PROVIDED", false);
        }
    }

    public record Selection(long garmentId, long variantId, long sizeId,
                            List<Measurement> measurements, Validation validation, ModelSupport model) {
    }
}
