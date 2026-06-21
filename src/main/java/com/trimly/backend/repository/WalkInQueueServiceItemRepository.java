package com.trimly.backend.repository;

import com.trimly.backend.entity.WalkInQueueServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalkInQueueServiceItemRepository extends JpaRepository<WalkInQueueServiceItem, UUID> {

    List<WalkInQueueServiceItem> findByQueueEntryId(UUID queueEntryId);

}
