package com.trimly.backend.repository;

import com.trimly.backend.entity.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShift, UUID> {

    List<StaffShift> findByShopIdAndStaffUserId(UUID shopId, UUID staffUserId);

    List<StaffShift> findByShopId(UUID shopId);

    Optional<StaffShift> findByShopIdAndStaffUserIdAndDayOfWeek(UUID shopId, UUID staffUserId, Integer dayOfWeek);
}