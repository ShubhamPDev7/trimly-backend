package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopHoursRepository extends JpaRepository<ShopHours, UUID> {

    List<ShopHours> findByShopId(UUID shopId);

    Optional<ShopHours> findByShopIdAndDayOfWeek(UUID shopId, int dayOfWeek);

}