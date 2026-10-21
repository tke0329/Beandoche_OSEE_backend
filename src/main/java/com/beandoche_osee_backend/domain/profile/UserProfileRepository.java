package com.beandoche_osee_backend.domain.profile;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByNicknameKeyAndUserIdNot(String nicknameKey, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProfile p where p.userId = :id")
    Optional<UserProfile> findLocked(@Param("id") Long id);
}
