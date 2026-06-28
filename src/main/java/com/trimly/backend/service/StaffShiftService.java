package com.trimly.backend.service;

import com.trimly.backend.dto.shift.StaffShiftRequest;
import com.trimly.backend.dto.shift.StaffShiftResponse;
import com.trimly.backend.entity.StaffShift;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.StaffShiftRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffShiftService {

    private final StaffShiftRepository staffShiftRepository;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;

    @Transactional
    public StaffShiftResponse upsertShift(UUID shopId, UUID staffUserId,
                                          StaffShiftRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        if (!shopAccessService.hasShopAccess(staffUserId, shopId)) {
            throw new IllegalArgumentException("Staff member does not belong to this shop.");
        }

        if (request.startTime().isAfter(request.endTime()) && !request.isOff()) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }

        StaffShift shift = staffShiftRepository
                .findByShopIdAndStaffUserIdAndDayOfWeek(shopId, staffUserId, request.dayOfWeek())
                .orElse(StaffShift.builder()
                        .shopId(shopId)
                        .staffUserId(staffUserId)
                        .build());

        shift.setDayOfWeek(request.dayOfWeek());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setOff(request.isOff());

        return toResponse(staffShiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public List<StaffShiftResponse> getStaffSchedule(UUID shopId, UUID staffUserId) {
        return staffShiftRepository.findByShopIdAndStaffUserId(shopId, staffUserId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffShiftResponse> getShopSchedule(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        return staffShiftRepository.findByShopId(shopId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteShift(UUID shopId, UUID staffUserId, Integer dayOfWeek, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        StaffShift shift = staffShiftRepository
                .findByShopIdAndStaffUserIdAndDayOfWeek(shopId, staffUserId, dayOfWeek)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found."));

        staffShiftRepository.delete(shift);
    }

    private StaffShiftResponse toResponse(StaffShift s) {
        String staffName = userRepository.findById(s.getStaffUserId())
                .map(User::getName)
                .orElse("Unknown");

        String dayName = DayOfWeek.of(s.getDayOfWeek()).name();

        return new StaffShiftResponse(
                s.getId(),
                s.getShopId(),
                s.getStaffUserId(),
                staffName,
                s.getDayOfWeek(),
                dayName,
                s.getStartTime(),
                s.getEndTime(),
                s.isOff(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}