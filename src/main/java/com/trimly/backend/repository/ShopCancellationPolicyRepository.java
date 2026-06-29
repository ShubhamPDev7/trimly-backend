package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopCancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopCancellationPolicyRepository extends JpaRepository<ShopCancellationPolicy, UUID> {
    Optional<ShopCancellationPolicy> findByShopId(UUID shopId);
    void deleteByShopId(UUID shopId);
}