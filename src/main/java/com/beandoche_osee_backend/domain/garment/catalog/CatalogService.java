package com.beandoche_osee_backend.domain.garment.catalog;

import com.beandoche_osee_backend.domain.category.dto.CategoryResponse;
import com.beandoche_osee_backend.domain.category.service.CategoryService;
import com.beandoche_osee_backend.domain.garment.service.GarmentFailure;
import com.beandoche_osee_backend.domain.garment.service.GarmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogStore store;
    private final GarmentService garments;
    private final CategoryService categories;
    private final MeasurementValidator validator;
    private final MaterialPolicy materialPolicy;

    public CatalogService(CatalogStore store, GarmentService garments, CategoryService categories,
                          MeasurementValidator validator, MaterialPolicy materialPolicy) {
        this.store = store;
        this.garments = garments;
        this.categories = categories;
        this.validator = validator;
        this.materialPolicy = materialPolicy;
    }

    public Options options(long garmentId) {
        var garment = garments.getGarment(garmentId);
        var materialStatus = store.materialStatus(garmentId);
        var materials = store.materials(garmentId);
        if (!materialPolicy.valid(materialStatus, materials)) {
            throw GarmentFailure.catalogDataInvalid();
        }
        var sizesByVariant = new HashMap<Long, List<Size>>();
        for (Size size : store.sizes(garmentId)) {
            sizesByVariant.computeIfAbsent(size.garmentVariantId(), key -> new ArrayList<>()).add(size);
        }
        var variants = store.variants(garmentId).stream().filter(Variant::active)
                .map(variant -> new VariantResponse(variant.id(), variant.colorName(), variant.colorCode(),
                        variant.colorHex(), variant.thumbnailUrl(),
                        List.copyOf(sizesByVariant.getOrDefault(variant.id(), List.of())))).toList();
        return new Options(materialStatus, materials, variants,
                store.rules(garment.categoryId()).stream().filter(MeasurementRule::active).toList());
    }

    public Selection select(long garmentId, long variantId, long sizeId) {
        var garment = garments.getGarment(garmentId);
        var variant = store.variants(garmentId).stream().filter(value -> value.id() == variantId)
                .findFirst().orElseThrow(GarmentFailure::invalidOption);
        if (!variant.active()) throw GarmentFailure.invalidOption();
        var size = store.sizes(garmentId).stream().filter(value -> value.id() == sizeId)
                .findFirst().orElseThrow(GarmentFailure::invalidOption);
        if (size.garmentVariantId() != variantId || !size.available()) {
            throw GarmentFailure.invalidOption();
        }
        var rules = store.rules(garment.categoryId());
        var values = store.measurements(sizeId);
        var orderedValues = new ArrayList<Measurement>();
        for (var rule : rules) {
            if (rule.active()) {
                values.stream().filter(value -> value.definitionId() == rule.id()).forEach(orderedValues::add);
            }
        }
        return new Selection(garmentId, variantId, sizeId, List.copyOf(orderedValues),
                validator.validate(rules, values), ModelSupport.notProvided());
    }

    public List<MeasurementRule> categoryMeasurements(long categoryId) {
        if (!contains(categories.getCategories(), categoryId)) throw GarmentFailure.categoryNotFound();
        return store.rules(categoryId).stream().filter(MeasurementRule::active).toList();
    }

    private boolean contains(List<CategoryResponse> tree, long id) {
        return tree.stream().anyMatch(category -> category.id() == id || contains(category.children(), id));
    }
}
