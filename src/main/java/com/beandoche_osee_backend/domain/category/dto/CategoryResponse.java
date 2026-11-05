package com.beandoche_osee_backend.domain.category.dto;

import java.util.List;

public record CategoryResponse(
        Long id,
        String code,
        String name,
        List<CategoryResponse> children
) {
}
