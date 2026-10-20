package com.beandoche_osee_backend.domain.auth.repository;

import java.util.Optional;

import com.beandoche_osee_backend.domain.auth.entity.SocialAccount;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    java.util.List<SocialAccount> findAllByUserId(Long userId);

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId);
}
