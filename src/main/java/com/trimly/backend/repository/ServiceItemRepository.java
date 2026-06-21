package com.trimly.backend.repository;

import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.enums.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {

    List<ServiceItem> findByShopIdAndDeletedFalse(UUID shopId);
    List<ServiceItem> findByShopIdAndCategoryAndDeletedFalse(UUID shopId, ServiceCategory category);

}
