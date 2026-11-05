package com.beandoche_osee_backend.domain.category.repository;

import com.beandoche_osee_backend.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
                    SELECT c
                    FROM Category c
                    WHERE c.active = true
                    ORDER BY c.displayOrder ASC, c.id ASC
            """)
    List<Category> findActiveCategories();
}
