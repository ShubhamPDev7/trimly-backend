package com.trimly.backend.repository;

import com.trimly.backend.entity.ServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {

    Optional<ServiceRecord> findByBookingId(UUID bookingId);

    Optional<ServiceRecord> findByWalkInQueueEntryId(UUID walkInQueueEntryId);

    List<ServiceRecord> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    List<ServiceRecord> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}