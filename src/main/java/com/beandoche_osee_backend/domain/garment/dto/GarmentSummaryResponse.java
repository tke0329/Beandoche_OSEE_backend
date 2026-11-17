package com.beandoche_osee_backend.domain.garment.dto;

import com.beandoche_osee_backend.domain.garment.entity.Garment;

public record GarmentSummaryResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String name,
        String thumbnailUrl
) {
    public static GarmentSummaryResponse from(Garment garment) {
        var category = garment.getCategory();
        return new GarmentSummaryResponse(garment.getId(), category.getId(), category.getCode(),
                category.getName(), garment.getName(), garment.getThumbnailUrl());
    }
}
