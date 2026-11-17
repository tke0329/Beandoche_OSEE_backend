package com.beandoche_osee_backend.domain.garment.repository;

import com.beandoche_osee_backend.domain.garment.entity.Garment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface GarmentRepository extends JpaRepository<Garment, Long> {

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT g FROM Garment g
            WHERE g.status = com.beandoche_osee_backend.domain.garment.entity.GarmentStatus.ACTIVE
              AND g.category.id IN :categoryIds
            """)
    Page<Garment> findPublished(@Param("categoryIds") Collection<Long> categoryIds, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT g FROM Garment g
            WHERE g.id = :id
              AND g.status = com.beandoche_osee_backend.domain.garment.entity.GarmentStatus.ACTIVE
            """)
    Optional<Garment> findPublishedById(@Param("id") Long id);
}
