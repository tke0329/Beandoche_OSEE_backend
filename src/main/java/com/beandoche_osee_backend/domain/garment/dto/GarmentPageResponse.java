package com.beandoche_osee_backend.domain.garment.dto;

import java.util.List;

public record GarmentPageResponse(
        List<GarmentSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
