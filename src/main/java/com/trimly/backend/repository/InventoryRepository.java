package com.trimly.backend.repository;

import com.trimly.backend.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findByShopId(UUID shopId);

    List<InventoryItem> findByShopIdAndQuantityInStockLessThanEqual(UUID shopId, BigDecimal threshold);
}