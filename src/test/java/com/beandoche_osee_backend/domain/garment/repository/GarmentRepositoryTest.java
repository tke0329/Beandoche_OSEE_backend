package com.beandoche_osee_backend.domain.garment.repository;

import com.beandoche_osee_backend.domain.garment.entity.Garment;
import com.beandoche_osee_backend.domain.garment.entity.GarmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GarmentRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private GarmentRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("t1 ACTIVE 상품만 카테고리별 최신순으로 페이지 조회한다")
    void t1_pagesOnlyPublishedGarments() {
        Long category = category("QUERY_TEST");
        Long other = category("QUERY_OTHER");
        Long old = garment(category, "ACTIVE", "2026-01-01T00:00:00Z");
        Long first = garment(category, "ACTIVE", "2026-02-01T00:00:00Z");
        Long second = garment(category, "ACTIVE", "2026-02-01T00:00:00Z");
        for (String status : List.of("DRAFT", "INACTIVE", "DELETED")) {
            garment(category, status, "2026-03-01T00:00:00Z");
        }
        garment(other, "ACTIVE", "2026-03-01T00:00:00Z");
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        var page = repository.findPublished(List.of(category), PageRequest.of(0, 2, sort));
        assertThat(page.getContent()).extracting(Garment::getId).containsExactly(second, first);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(repository.findPublished(List.of(category), PageRequest.of(1, 2, sort)).getContent())
                .extracting(Garment::getId).containsExactly(old);
        assertThat(page.getContent().getFirst().getCategory().getCode()).isEqualTo("QUERY_TEST");
    }

    @Test
    @DisplayName("t2 상세 조회는 ACTIVE 상태만 반환한다")
    void t2_hidesUnpublishedDetail() {
        Long category = category("DETAIL_TEST");
        for (GarmentStatus status : GarmentStatus.values()) {
            Long id = garment(category, status.name(), "2026-01-01T00:00:00Z");
            assertThat(repository.findPublishedById(id).isPresent()).isEqualTo(status == GarmentStatus.ACTIVE);
        }
        assertThat(repository.findPublishedById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("t3 조회 상품의 수정 시각을 갱신하고 생성 시각을 보존한다")
    void t3_updatesTimestamp() {
        Long category = category("TIME_TEST");
        Long id = garment(category, "ACTIVE", "2026-01-01T00:00:00Z");
        Garment garment = repository.findPublishedById(id).orElseThrow();
        var created = garment.getCreatedAt();
        var updated = garment.getUpdatedAt();
        org.springframework.test.util.ReflectionTestUtils.setField(garment, "name", "변경 상품");
        entityManager.flush();
        entityManager.clear();
        Garment reloaded = repository.findPublishedById(id).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(created);
        assertThat(reloaded.getUpdatedAt()).isAfter(updated);
    }

    @Test
    @WithMockUser
    @DisplayName("t4 실제 조회 API는 대분류 하위 상품만 반환하고 비공개 상품을 숨긴다")
    void t4_queriesCategoryThroughApi() throws Exception {
        Long root = category("API_ROOT");
        Long child = childCategory("API_CHILD", root);
        Long other = category("API_OTHER");
        Long id = garment(child, "ACTIVE", "2026-01-01T00:00:00Z");
        garment(other, "ACTIVE", "2026-01-01T00:00:00Z");
        Long draft = garment(child, "DRAFT", "2026-01-01T00:00:00Z");
        mvc.perform(get("/api/garments").param("categoryCode", "API_ROOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/garments/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("상품 설명"));
        mvc.perform(get("/api/garments/" + draft)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GARMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    @DisplayName("t5 비활성 조상 아래 상품은 목록과 상세에서 제외한다")
    void t5_hidesInactiveCategoryThroughApi() throws Exception {
        Long root = category("HIDDEN_ROOT");
        Long child = childCategory("HIDDEN_CHILD", root);
        Long leaf = childCategory("HIDDEN_LEAF", child);
        Long id = garment(leaf, "ACTIVE", "2026-01-01T00:00:00Z");
        jdbc.update("UPDATE category SET is_active = false WHERE id = ?", root);
        mvc.perform(get("/api/garments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/garments").param("categoryCode", "HIDDEN_LEAF"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mvc.perform(get("/api/garments/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("t6 신규 JPA 상품은 DRAFT로 저장되고 생성 및 수정 시각이 채워진다")
    void t6_persistsDraftWithTimestamps() {
        Long categoryId = category("PERSIST_TEST");
        Garment garment = org.springframework.beans.BeanUtils.instantiateClass(Garment.class);
        org.springframework.test.util.ReflectionTestUtils.setField(garment, "category",
                entityManager.getReference(com.beandoche_osee_backend.domain.category.entity.Category.class, categoryId));
        org.springframework.test.util.ReflectionTestUtils.setField(garment, "name", "새 상품");
        org.springframework.test.util.ReflectionTestUtils.setField(garment, "description", "설명");
        org.springframework.test.util.ReflectionTestUtils.setField(garment, "thumbnailUrl", "/new.jpg");
        repository.saveAndFlush(garment);
        entityManager.clear();
        Garment reloaded = repository.findById(garment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(GarmentStatus.DRAFT);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @WithMockUser
    @DisplayName("t7 페이지 범위와 정렬 오류 및 없는 카테고리를 실제 API에서 처리한다")
    void t7_rejectsInvalidRequestsThroughApi() throws Exception {
        mvc.perform(get("/api/garments").param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/garments").param("sort", "PRICE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        mvc.perform(get("/api/garments").param("categoryCode", "MISSING_CATEGORY"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    private Long childCategory(String code, Long parent) {
        return jdbc.queryForObject("INSERT INTO category (parent_id, code, name) VALUES (?, ?, ?) RETURNING id",
                Long.class, parent, code, code);
    }

    private Long category(String code) {
        return jdbc.queryForObject("INSERT INTO category (code, name) VALUES (?, ?) RETURNING id",
                Long.class, code, code);
    }

    private Long garment(Long category, String status, String created) {
        return jdbc.queryForObject("""
                INSERT INTO garment (category_id, name, description, thumbnail_url, status, created_at, updated_at)
                VALUES (?, '테스트 상품', '상품 설명', '/test.jpg', ?, CAST(? AS timestamptz), CAST(? AS timestamptz))
                RETURNING id
                """, Long.class, category, status, created, created);
    }
}
