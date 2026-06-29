package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, UUID> {

    Optional<ShopSubscription> findByShopId(UUID shopId);

    Optional<ShopSubscription> findByRazorpayOrderId(String razorpayOrderId);

    @Query("SELECT s FROM ShopSubscription s WHERE s.status = 'ACTIVE' AND s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    List<ShopSubscription> findExpiredSubscriptions(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

}