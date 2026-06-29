package com.trimly.backend.service;

import com.trimly.backend.dto.leave.StaffLeaveRequest;
import com.trimly.backend.dto.leave.StaffLeaveResponse;
import com.trimly.backend.entity.StaffLeave;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.StaffLeaveRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffLeaveService {

    private final StaffLeaveRepository staffLeaveRepository;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;

    @Transactional
    public StaffLeaveResponse markLeave(UUID shopId, UUID staffUserId, StaffLeaveRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        if (!shopAccessService.hasShopAccess(staffUserId, shopId)) {
            throw new IllegalArgumentException("Staff member does not belong to this shop.");
        }

        if (request.leaveDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot mark leave for a past date.");
        }

        if (staffLeaveRepository.existsByShopIdAndStaffUserIdAndLeaveDate(shopId, staffUserId, request.leaveDate())) {
            throw new IllegalArgumentException("Leave already marked for this date.");
        }

        StaffLeave leave = StaffLeave.builder()
                .shopId(shopId)
                .staffUserId(staffUserId)
                .leaveDate(request.leaveDate())
                .reason(request.reason())
                .build();

        return toResponse(staffLeaveRepository.save(leave));
    }

    @Transactional
    public void cancelLeave(UUID shopId, UUID staffUserId, LocalDate leaveDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        StaffLeave leave = staffLeaveRepository
                .findByShopIdAndStaffUserIdAndLeaveDate(shopId, staffUserId, leaveDate)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found."));

        staffLeaveRepository.delete(leave);
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveResponse> getLeaves(UUID shopId, UUID staffUserId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        return staffLeaveRepository.findByShopIdAndStaffUserId(shopId, staffUserId)
                .stream().map(this::toResponse).toList();
    }

    private StaffLeaveResponse toResponse(StaffLeave leave) {
        String staffName = userRepository.findById(leave.getStaffUserId())
                .map(User::getName)
                .orElse("Unknown");

        return new StaffLeaveResponse(
                leave.getId(),
                leave.getShopId(),
                leave.getStaffUserId(),
                staffName,
                leave.getLeaveDate(),
                leave.getReason(),
                leave.getCreatedAt()
        );
    }
}