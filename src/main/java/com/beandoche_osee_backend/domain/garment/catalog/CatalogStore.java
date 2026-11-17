package com.beandoche_osee_backend.domain.garment.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;

@Repository
public class CatalogStore {
    private final JdbcTemplate jdbc;

    public CatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Variant> variants(long garmentId) {
        return jdbc.query("SELECT * FROM garment_variant WHERE garment_id = ? ORDER BY id",
                (rs, row) -> new Variant(rs.getLong("id"), rs.getLong("garment_id"), rs.getString("color_name"),
                        rs.getString("color_code"), rs.getString("color_hex"), rs.getString("thumbnail_url"),
                        rs.getBoolean("is_active")), garmentId);
    }

    public List<Size> sizes(long garmentId) {
        return jdbc.query("""
                SELECT s.* FROM garment_size s JOIN garment_variant v ON v.id = s.garment_variant_id
                WHERE v.garment_id = ? ORDER BY s.sort_order, s.id
                """, (rs, row) -> new Size(rs.getLong("id"), rs.getLong("garment_variant_id"),
                rs.getString("size_label"), rs.getInt("sort_order"), rs.getString("sku"),
                rs.getBoolean("is_available")), garmentId);
    }

    public MaterialStatus materialStatus(long garmentId) {
        return MaterialStatus.valueOf(jdbc.queryForObject(
                "SELECT material_status FROM garment WHERE id = ?", String.class, garmentId));
    }

    public List<MaterialShare> materials(long garmentId) {
        return jdbc.query("""
                SELECT m.id, m.code, m.name, gm.percentage FROM garment_material gm
                JOIN material m ON m.id = gm.material_id WHERE gm.garment_id = ? ORDER BY m.id
                """, (rs, row) -> new MaterialShare(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getBigDecimal("percentage")), garmentId);
    }

    public List<MeasurementRule> rules(long categoryId) {
        return jdbc.query("""
                SELECT d.*, cm.is_required, cm.display_order, cm.fit_analysis_enabled,
                       cm.min_value_mm, cm.max_value_mm FROM category_measurement cm
                JOIN measurement_definition d ON d.id = cm.measurement_definition_id
                WHERE cm.category_id = ? ORDER BY cm.display_order, d.id
                """, (rs, row) -> new MeasurementRule(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("default_unit"),
                MeasurementBasis.valueOf(rs.getString("measurement_basis")), rs.getString("description"),
                rs.getBoolean("is_active"), rs.getBoolean("is_required"), rs.getInt("display_order"),
                rs.getBoolean("fit_analysis_enabled"), rs.getBigDecimal("min_value_mm"),
                rs.getBigDecimal("max_value_mm")), categoryId);
    }

    public List<Measurement> measurements(long sizeId) {
        return jdbc.query("""
                SELECT d.id, d.code, d.name, d.default_unit, d.measurement_basis,
                       sm.value_mm, sm.source, sm.measured_at FROM size_measurement sm
                JOIN measurement_definition d ON d.id = sm.measurement_definition_id
                WHERE sm.garment_size_id = ? ORDER BY d.id
                """, (rs, row) -> new Measurement(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("default_unit"), MeasurementBasis.valueOf(rs.getString("measurement_basis")),
                rs.getBigDecimal("value_mm"), MeasurementSource.valueOf(rs.getString("source")),
                rs.getTimestamp("measured_at").toInstant()), sizeId);
    }
}
