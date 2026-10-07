package com.beandoche_osee_backend.domain.auth.repository;

import java.util.Optional;

import com.beandoche_osee_backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
