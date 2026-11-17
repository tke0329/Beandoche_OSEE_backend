package com.beandoche_osee_backend.domain.garment.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;

@RestController
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/garments/{id}/options")
    public Options options(@PathVariable long id) {
        return service.options(id);
    }

    @GetMapping("/api/garments/{id}/selection")
    public Selection selection(@PathVariable long id, @RequestParam long variantId, @RequestParam long sizeId) {
        return service.select(id, variantId, sizeId);
    }

    @GetMapping("/api/categories/{id}/measurements")
    public List<MeasurementRule> categoryMeasurements(@PathVariable long id) {
        return service.categoryMeasurements(id);
    }
}
