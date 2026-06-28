package com.trimly.backend.repository;

import com.trimly.backend.entity.InventoryUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryUsageRepository extends JpaRepository<InventoryUsage, UUID> {

    List<InventoryUsage> findByServiceRecordId(UUID serviceRecordId);
}