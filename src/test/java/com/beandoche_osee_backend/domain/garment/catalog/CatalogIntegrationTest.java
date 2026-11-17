package com.beandoche_osee_backend.domain.garment.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private CatalogService service;

    @Test
    @WithMockUser
    @DisplayName("t1 새 상품에 DEFAULT 색상을 만들고 소재 미확인 상태를 반환한다")
    void t1_returnsDefaultVariant() throws Exception {
        long product = product("ACTIVE");
        mvc.perform(get("/api/garments/" + product + "/options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.materialStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.materials").isEmpty())
                .andExpect(jsonPath("$.variants[0].colorCode").value("DEFAULT"))
                .andExpect(jsonPath("$.variants[0].sizes").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("t2 비활성 색상을 숨기고 사이즈의 순서와 선택 가능 여부를 반환한다")
    void t2_filtersAndOrdersOptions() throws Exception {
        long product = product("ACTIVE");
        long variant = variant(product);
        size(variant, "L", 2, true);
        size(variant, "FREE", 1, false);
        jdbc.update("INSERT INTO garment_variant(garment_id,color_code,color_name,is_active) VALUES (?, 'HIDDEN','숨김',false)", product);
        mvc.perform(get("/api/garments/" + product + "/options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sizes[0].sizeLabel").value("FREE"))
                .andExpect(jsonPath("$.variants[0].sizes[0].available").value(false))
                .andExpect(jsonPath("$.variants[0].sizes[1].sizeLabel").value("L"));
    }

    @Test
    @WithMockUser
    @DisplayName("t3 상품과 무관한 색상 및 사이즈 조합을 거부한다")
    void t3_rejectsUnrelatedOptions() throws Exception {
        long product = product("ACTIVE");
        long another = product("ACTIVE");
        long variant = variant(another);
        long size = size(variant, "M", 0, true);
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "" + variant).param("sizeId", "" + size))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GARMENT_OPTION"));
        long firstVariant = variant(product);
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "" + firstVariant).param("sizeId", "" + size))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("t4 비활성 색상과 판매 불가 사이즈를 선택할 수 없다")
    void t4_rejectsInactiveOptions() throws Exception {
        long product = product("ACTIVE");
        long variant = variant(product);
        long size = size(variant, "M", 0, false);
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "" + variant).param("sizeId", "" + size))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE garment_size SET is_available=true WHERE id=?", size);
        jdbc.update("UPDATE garment_variant SET is_active=false WHERE id=?", variant);
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "" + variant).param("sizeId", "" + size))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("t5 실측을 mm로 반환하고 범위 미설정과 3D 미제공 상태를 표시한다")
    void t5_reportsUnconfiguredRange() throws Exception {
        long product = product("ACTIVE");
        long variant = variant(product);
        long size = size(variant, "M", 0, true);
        long definition = definition("FLAT_WIDTH", "mm");
        rule(definition);
        measurement(size, definition, "500");
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "" + variant).param("sizeId", "" + size))
                .andExpect(status().isOk()).andExpect(jsonPath("$.measurements[0].valueMm").value(500))
                .andExpect(jsonPath("$.measurements[0].measurementBasis").value("FLAT_WIDTH"))
                .andExpect(jsonPath("$.validation.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.validation.measurementReady").value(false))
                .andExpect(jsonPath("$.validation.issues[0].code").value("RANGE_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.model.status").value("NOT_PROVIDED"));
        jdbc.update("UPDATE category_measurement SET min_value_mm=100,max_value_mm=1000 WHERE measurement_definition_id=?", definition);
        assertThat(service.select(product, variant, size).validation().measurementReady()).isTrue();
        assertThat(service.select(product, variant, size).model().available()).isFalse();
    }

    @Test
    @WithMockUser
    @DisplayName("t6 카테고리별 측정 방법과 필수 여부를 반환하고 비활성 항목을 숨긴다")
    void t6_returnsCategoryDefinitions() throws Exception {
        long active = definition("FLAT_WIDTH", "mm");
        long inactive = definition("LENGTH", "mm");
        rule(active); rule(inactive);
        jdbc.update("UPDATE measurement_definition SET is_active=false WHERE id=?", inactive);
        mvc.perform(get("/api/categories/" + categoryId() + "/measurements"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].description").value("테스트 측정 방법"));
        mvc.perform(get("/api/categories/9223372036854775807/measurements")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("t7 색상별 사이즈 라벨 중복을 DB에서 차단한다")
    void t7_enforcesUniqueSizeLabels() {
        long variant = variant(product("ACTIVE"));
        size(variant, "M", 0, true);
        assertThatThrownBy(() -> size(variant, "M", 1, true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t8 SKU 중복을 DB에서 차단한다")
    void t8_enforcesUniqueSku() {
        long variant = variant(product("ACTIVE"));
        long id = size(variant, "M", 0, true);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO garment_size(garment_variant_id,size_label,sku) SELECT garment_variant_id,'L',sku FROM garment_size WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t9 사이즈 실측의 중복을 DB에서 차단한다")
    void t9_enforcesUniqueMeasurement() {
        long size = size(variant(product("ACTIVE")), "M", 0, true);
        long definition = definition("LENGTH", "mm");
        measurement(size, definition, "500");
        assertThatThrownBy(() -> measurement(size, definition, "501")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t10 양수가 아닌 실측을 DB에서 차단한다")
    void t10_rejectsNonPositiveMeasurement() {
        long size = size(variant(product("ACTIVE")), "M", 0, true);
        long definition = definition("LENGTH", "mm");
        assertThatThrownBy(() -> measurement(size, definition, "0")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t11 무게 항목을 value_mm에 저장할 수 없다")
    void t11_rejectsWeightInLengthColumn() {
        long size = size(variant(product("ACTIVE")), "M", 0, true);
        long definition = definition("WEIGHT", "g");
        assertThatThrownBy(() -> measurement(size, definition, "500")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("t12 확인된 소재의 합계가 100이 아니면 트랜잭션을 거부한다")
    void t12_enforcesKnownMaterialTotal() {
        long product = product("ACTIVE");
        jdbc.update("UPDATE garment SET material_status='KNOWN' WHERE id=?", product);
        assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @WithMockUser
    @DisplayName("t13 소재와 혼용률을 상세 옵션에 반환한다")
    void t13_returnsKnownMaterials() throws Exception {
        long product = product("ACTIVE");
        long material = jdbc.queryForObject("INSERT INTO material(code,name) VALUES (?, '면') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO garment_material(garment_id,material_id,percentage) VALUES (?,?,100)", product, material);
        jdbc.update("UPDATE garment SET material_status='KNOWN' WHERE id=?", product);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        mvc.perform(get("/api/garments/" + product + "/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.materialStatus").value("KNOWN"))
                .andExpect(jsonPath("$.materials[0].name").value("면"))
                .andExpect(jsonPath("$.materials[0].percentage").value(100));
    }

    @Test
    @WithMockUser
    @DisplayName("t14 비공개 상품의 옵션과 선택 결과를 숨긴다")
    void t14_hidesUnpublishedProduct() throws Exception {
        long product = product("DRAFT");
        mvc.perform(get("/api/garments/" + product + "/options")).andExpect(status().isNotFound());
        mvc.perform(get("/api/garments/" + product + "/selection").param("variantId", "1").param("sizeId", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("t15 옵션과 측정 항목 API에 인증이 필요하다")
    void t15_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/garments/1/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/garments/1/selection").param("variantId", "1").param("sizeId", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories/1/measurements")).andExpect(status().isUnauthorized());
    }

    private long categoryId() {
        return jdbc.queryForObject("SELECT id FROM category WHERE code='SHORT_SLEEVE_T_SHIRT'", Long.class);
    }

    private long product(String status) {
        return jdbc.queryForObject("INSERT INTO garment(category_id,name,description,thumbnail_url,status) VALUES (?, '테스트 상품','설명','/test.jpg',?) RETURNING id", Long.class, categoryId(), status);
    }

    private long variant(long product) {
        return jdbc.queryForObject("SELECT id FROM garment_variant WHERE garment_id=? AND color_code='DEFAULT'", Long.class, product);
    }

    private long size(long variant, String label, int order, boolean available) {
        return jdbc.queryForObject("INSERT INTO garment_size(garment_variant_id,size_label,sort_order,sku,is_available) VALUES (?,?,?,?,?) RETURNING id", Long.class, variant, label, order, UUID.randomUUID().toString(), available);
    }

    private long definition(String basis, String unit) {
        return jdbc.queryForObject("INSERT INTO measurement_definition(code,name,default_unit,measurement_basis,description) VALUES (?, '가슴단면',?,?,'테스트 측정 방법') RETURNING id", Long.class, UUID.randomUUID().toString(), unit, basis);
    }

    private void rule(long definition) {
        jdbc.update("INSERT INTO category_measurement(category_id,measurement_definition_id,is_required,display_order,fit_analysis_enabled) VALUES (?,?,true,1,true)", categoryId(), definition);
    }

    private void measurement(long size, long definition, String value) {
        jdbc.update("INSERT INTO size_measurement(garment_size_id,measurement_definition_id,value_mm,source,measured_at) VALUES (?,?,CAST(? AS numeric),'DIRECT_MEASUREMENT',CURRENT_TIMESTAMP)", size, definition, value);
    }
}
