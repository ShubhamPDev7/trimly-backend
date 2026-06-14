package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopStaffRepository extends JpaRepository<ShopStaff, UUID> {

    List<ShopStaff> findByUserId(UUID userId);

    List<ShopStaff> findByShopId(UUID shopId);

    Optional<ShopStaff> findByShopIdAndUserId(UUID shopId, UUID userId);

}
