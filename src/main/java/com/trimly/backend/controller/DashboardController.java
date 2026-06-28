package com.trimly.backend.controller;

import com.trimly.backend.dto.dashboard.DashboardSummaryResponse;
import com.trimly.backend.dto.dashboard.PeakHoursResponse;
import com.trimly.backend.dto.dashboard.ShopOverviewResponse;
import com.trimly.backend.dto.dashboard.StaffPerformanceResponse;
import com.trimly.backend.dto.dashboard.TopServicesResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.DashboardService;
import com.trimly.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.enforceDashboardAccess(shopId);
        return ResponseEntity.ok(dashboardService.getSummary(shopId, startDate, endDate, userDetails.getUser().getId()));
    }

    @GetMapping("/staff-performance")
    public ResponseEntity<List<StaffPerformanceResponse>> getStaffPerformance(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.enforceDashboardAccess(shopId);
        return ResponseEntity.ok(dashboardService.getStaffPerformance(shopId, startDate, endDate, userDetails.getUser().getId()));
    }

    @GetMapping("/peak-hours")
    public ResponseEntity<PeakHoursResponse> getPeakHours(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.enforceDashboardAccess(shopId);
        return ResponseEntity.ok(dashboardService.getPeakHours(shopId, startDate, endDate, userDetails.getUser().getId()));
    }

    @GetMapping("/top-services")
    public ResponseEntity<TopServicesResponse> getTopServices(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.enforceDashboardAccess(shopId);
        return ResponseEntity.ok(dashboardService.getTopServices(shopId, startDate, endDate, userDetails.getUser().getId()));
    }

    @GetMapping("/overview")
    public ResponseEntity<ShopOverviewResponse> getOverview(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.enforceDashboardAccess(shopId);
        return ResponseEntity.ok(dashboardService.getOverview(shopId, startDate, endDate, userDetails.getUser().getId()));
    }
}