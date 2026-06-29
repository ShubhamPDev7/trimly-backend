package com.trimly.backend.controller;

import com.trimly.backend.dto.leave.StaffLeaveRequest;
import com.trimly.backend.dto.leave.StaffLeaveResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.StaffLeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StaffLeaveController {

    private final StaffLeaveService staffLeaveService;

    @PostMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/leaves")
    public ResponseEntity<StaffLeaveResponse> markLeave(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @Valid @RequestBody StaffLeaveRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                staffLeaveService.markLeave(shopId, staffUserId, request, userDetails.getUser().getId()));
    }

    @DeleteMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/leaves/{leaveDate}")
    public ResponseEntity<Void> cancelLeave(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaveDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        staffLeaveService.cancelLeave(shopId, staffUserId, leaveDate, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/leaves")
    public ResponseEntity<List<StaffLeaveResponse>> getLeaves(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                staffLeaveService.getLeaves(shopId, staffUserId, userDetails.getUser().getId()));
    }
}