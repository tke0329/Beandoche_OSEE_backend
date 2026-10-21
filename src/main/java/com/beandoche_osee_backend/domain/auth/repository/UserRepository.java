package com.beandoche_osee_backend.domain.auth.repository;

import com.beandoche_osee_backend.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    java.util.Optional<User> findLocked(@org.springframework.data.repository.query.Param("id") Long id);
}
