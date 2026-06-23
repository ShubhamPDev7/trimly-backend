package com.trimly.backend.repository;

import com.trimly.backend.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {

    Optional<LoyaltyAccount> findByShopIdAndCustomerId(UUID shopId, UUID customerId);

}