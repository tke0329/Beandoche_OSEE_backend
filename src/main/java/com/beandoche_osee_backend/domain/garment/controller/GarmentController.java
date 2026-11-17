package com.beandoche_osee_backend.domain.garment.controller;

import com.beandoche_osee_backend.domain.garment.dto.GarmentDetailResponse;
import com.beandoche_osee_backend.domain.garment.dto.GarmentPageResponse;
import com.beandoche_osee_backend.domain.garment.service.GarmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/garments")
public class GarmentController {
    private final GarmentService service;

    public GarmentController(GarmentService service) {
        this.service = service;
    }

    @GetMapping
    public GarmentPageResponse getGarments(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "LATEST") String sort) {
        return service.getGarments(categoryCode, page, size, sort);
    }

    @GetMapping("/{id}")
    public GarmentDetailResponse getGarment(@PathVariable Long id) {
        return service.getGarment(id);
    }
}
