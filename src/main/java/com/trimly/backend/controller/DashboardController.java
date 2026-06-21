package com.trimly.backend.controller;

import com.trimly.backend.dto.dashboard.DashboardSummaryResponse;
import com.trimly.backend.dto.dashboard.StaffPerformanceResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(dashboardService.getSummary(shopId, startDate, endDate, userDetails.getUser().getId()));
    }

    @GetMapping("/staff-performance")
    public ResponseEntity<List<StaffPerformanceResponse>> getStaffPerformance(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(dashboardService.getStaffPerformance(shopId, startDate, endDate, userDetails.getUser().getId()));
    }
}