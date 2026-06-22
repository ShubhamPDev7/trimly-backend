package com.trimly.backend.repository;

import com.trimly.backend.entity.ShopClosedDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopClosedDateRepository extends JpaRepository<ShopClosedDate, UUID> {

    List<ShopClosedDate> findByShopIdAndClosedDateGreaterThanEqual(UUID shopId, LocalDate from);

    Optional<ShopClosedDate> findByShopIdAndClosedDate(UUID shopId, LocalDate date);

    boolean existsByShopIdAndClosedDate(UUID shopId, LocalDate date);

}