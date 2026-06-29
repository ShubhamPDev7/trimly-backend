package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, UUID> {

    Optional<ShopSubscription> findByShopId(UUID shopId);

    Optional<ShopSubscription> findByRazorpayOrderId(String razorpayOrderId);
}