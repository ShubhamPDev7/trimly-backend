package com.trimly.backend.controller;

import com.trimly.backend.dto.shift.StaffShiftRequest;
import com.trimly.backend.dto.shift.StaffShiftResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.StaffShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StaffShiftController {

    private final StaffShiftService staffShiftService;

    @PutMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/shifts")
    public ResponseEntity<StaffShiftResponse> upsertShift(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @Valid @RequestBody StaffShiftRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                staffShiftService.upsertShift(shopId, staffUserId, request, userDetails.getUser().getId()));
    }

    @GetMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/shifts")
    public ResponseEntity<List<StaffShiftResponse>> getStaffSchedule(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId
    ) {
        return ResponseEntity.ok(staffShiftService.getStaffSchedule(shopId, staffUserId));
    }

    @GetMapping("/api/v1/shops/{shopId}/shifts")
    public ResponseEntity<List<StaffShiftResponse>> getShopSchedule(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(staffShiftService.getShopSchedule(shopId, userDetails.getUser().getId()));
    }

    @DeleteMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/shifts/{dayOfWeek}")
    public ResponseEntity<Void> deleteShift(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @PathVariable Integer dayOfWeek,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        staffShiftService.deleteShift(shopId, staffUserId, dayOfWeek, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}