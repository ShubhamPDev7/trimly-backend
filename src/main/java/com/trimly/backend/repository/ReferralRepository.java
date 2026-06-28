package com.trimly.backend.repository;

import com.trimly.backend.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    List<Referral> findByShopIdAndReferrerId(UUID shopId, UUID referrerId);

    Optional<Referral> findByReferralCodeAndShopId(String referralCode, UUID shopId);

    boolean existsByShopIdAndReferredId(UUID shopId, UUID referredId);

    Optional<Referral> findByShopIdAndReferredIdAndStatus(UUID shopId, UUID referredId, String status);
}