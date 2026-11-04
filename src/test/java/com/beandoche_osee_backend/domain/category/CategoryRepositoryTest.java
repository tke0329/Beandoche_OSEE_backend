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

        //Then : 활성 황목은 포함, 비활성 홤목은 제외
        assertThat(categories)
                .extracting(Category::getCode)
                .contains("TEST_ACTIVE")
                .doesNotContain("TEST_INACTIVE");

        assertThat(categories)
                .allMatch(Category::isActive);


    }
}
