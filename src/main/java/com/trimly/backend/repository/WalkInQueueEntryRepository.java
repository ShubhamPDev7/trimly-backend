package com.trimly.backend.repository;

import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.enums.WalkInStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalkInQueueEntryRepository extends JpaRepository<WalkInQueueEntry, UUID> {

    List<WalkInQueueEntry> findByShopIdAndStatusOrderByJoinedAtAsc(UUID shopId, WalkInStatus status);

    List<WalkInQueueEntry> findByShopIdAndStatusInOrderByJoinedAtAsc(UUID shopId, List<WalkInStatus> statuses);

    org.springframework.data.domain.Page<WalkInQueueEntry> findByShopIdOrderByJoinedAtDesc(
            UUID shopId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<WalkInQueueEntry> findByCustomerIdOrderByJoinedAtDesc(
            UUID customerId, org.springframework.data.domain.Pageable pageable);

}