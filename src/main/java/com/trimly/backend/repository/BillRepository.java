package com.trimly.backend.repository;

import com.trimly.backend.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByBookingId(UUID bookingId);
    List<Bill> findByShopId(UUID shopId);
    List<Bill> findByShopIdAndCreatedAtBetween(UUID shopId, Instant start, Instant end);

}
