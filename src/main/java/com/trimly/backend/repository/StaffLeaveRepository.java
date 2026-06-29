package com.trimly.backend.repository;

import com.trimly.backend.entity.StaffLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffLeaveRepository extends JpaRepository<StaffLeave, UUID> {

    boolean existsByShopIdAndStaffUserIdAndLeaveDate(UUID shopId, UUID staffUserId, LocalDate leaveDate);

    Optional<StaffLeave> findByShopIdAndStaffUserIdAndLeaveDate(UUID shopId, UUID staffUserId, LocalDate leaveDate);

    List<StaffLeave> findByShopIdAndStaffUserId(UUID shopId, UUID staffUserId);

    List<StaffLeave> findByShopIdAndStaffUserIdAndLeaveDateBetween(UUID shopId, UUID staffUserId, LocalDate from, LocalDate to);
}