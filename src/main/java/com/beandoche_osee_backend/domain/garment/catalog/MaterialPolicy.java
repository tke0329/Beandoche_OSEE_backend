package com.beandoche_osee_backend.domain.garment.catalog;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;

@Component
public class MaterialPolicy {
    public boolean valid(MaterialStatus status, List<MaterialShare> shares) {
        if (shares.stream().anyMatch(share -> share.percentage() == null || share.percentage().signum() <= 0
                || share.percentage().compareTo(BigDecimal.valueOf(100)) > 0)) {
            return false;
        }
        return status == MaterialStatus.UNKNOWN || shares.stream().map(MaterialShare::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(100)) == 0;
    }
}
