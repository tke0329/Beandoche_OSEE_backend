package com.beandoche_osee_backend.domain.category;

import com.beandoche_osee_backend.domain.category.entity.Category;
import com.beandoche_osee_backend.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
@SpringBootTest
@Transactional
class CategoryRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void registerPostgresProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;
    @Test
    @DisplayName("t1 활성 카테고리만 조회한다.")
    void t1_findOnlyActiveCategories(){
        //Given : 테스트 데이터 준비
        jdbcTemplate.update("""
                INSERT INTO category(code, name, display_order, is_active)
                VALUES(?,?,?,?)
                ""","TEST_ACTIVE","테스트 활성 카테고리",1,true);

        jdbcTemplate.update("""
                INSERT INTO category(code, name, display_order, is_active)
                VALUES(?,?,?,?)
                ""","TEST_INACTIVE","테스트 비활성 카테고리",2,false);

        //When : 검증할 조회 메서드 실행
        List<Category> categories = categoryRepository.findActiveCategories();

        //Then : 활성 항목은 포함, 비활성 항목은 제외
        assertThat(categories)
                .extracting(Category::getCode)
                .contains("TEST_ACTIVE")
                .doesNotContain("TEST_INACTIVE");

        assertThat(categories)
                .allMatch(Category::isActive);


    }
    @Test
    @DisplayName("t2 표시 순서가 같으면 ID 순으로 정렬한다")
    void t2_ordersByDisplayOrderThenId() {
        Long firstId = insertCategory("TEST_ORDER_FIRST", 20);
        Long secondId = insertCategory("TEST_ORDER_SECOND", 20);
        insertCategory("TEST_ORDER_EARLIER", 10);

        var result = categoryRepository.findActiveCategories().stream()
                .filter(category -> category.getCode().startsWith("TEST_ORDER_"))
                .toList();

        assertThat(result).extracting(Category::getCode)
                .containsExactly("TEST_ORDER_EARLIER", "TEST_ORDER_FIRST", "TEST_ORDER_SECOND");
        assertThat(firstId).isLessThan(secondId);
    }

    @Test
    @DisplayName("t3 하위 카테고리의 부모 관계를 조회한다")
    void t3_loadsParentRelationship() {
        Long parentId = insertCategory("TEST_PARENT", 1);
        jdbcTemplate.update("""
                INSERT INTO category (parent_id, code, name, display_order, is_active)
                VALUES (?, ?, ?, ?, ?)
                """, parentId, "TEST_CHILD", "테스트 자식", 1, true);

        var child = categoryRepository.findActiveCategories().stream()
                .filter(category -> category.getCode().equals("TEST_CHILD"))
                .findFirst().orElseThrow();

        assertThat(child.getParent().getId()).isEqualTo(parentId);
        assertThat(child.getParent().getCode()).isEqualTo("TEST_PARENT");
    }

    private Long insertCategory(String code, int order) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO category (code, name, display_order, is_active)
                VALUES (?, ?, ?, true)
                RETURNING id
                """, Long.class, code, "테스트 카테고리", order);
    }
}
