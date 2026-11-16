package com.beandoche_osee_backend.domain.garment.dto;

import com.beandoche_osee_backend.domain.garment.entity.Garment;

import java.time.Instant;

public record GarmentDetailResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String name,
        String description,
        String thumbnailUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static GarmentDetailResponse from(Garment garment) {
        var category = garment.getCategory();
        return new GarmentDetailResponse(garment.getId(), category.getId(), category.getCode(),
                category.getName(), garment.getName(), garment.getDescription(), garment.getThumbnailUrl(),
                garment.getCreatedAt(), garment.getUpdatedAt());
    }
}
