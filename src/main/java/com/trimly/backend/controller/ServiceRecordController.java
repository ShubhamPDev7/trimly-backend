package com.trimly.backend.controller;

import com.trimly.backend.dto.servicerecord.ServiceRecordRequest;
import com.trimly.backend.dto.servicerecord.ServiceRecordResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ServiceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ServiceRecordController {

    private final ServiceRecordService serviceRecordService;

    @PostMapping("/api/v1/shops/{shopId}/bookings/{bookingId}/service-record")
    public ResponseEntity<ServiceRecordResponse> createForBooking(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @RequestBody ServiceRecordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ServiceRecordResponse response = serviceRecordService.createForBooking(
                shopId, bookingId, request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/shops/{shopId}/walk-in-queue/{entryId}/service-record")
    public ResponseEntity<ServiceRecordResponse> createForWalkIn(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @RequestBody ServiceRecordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ServiceRecordResponse response = serviceRecordService.createForWalkIn(
                shopId, entryId, request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/shops/{shopId}/service-records")
    public ResponseEntity<List<ServiceRecordResponse>> getShopRecords(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRecordService.getShopRecords(shopId, userDetails.getUser()));
    }

    @GetMapping("/api/v1/customers/me/style-history")
    public ResponseEntity<List<ServiceRecordResponse>> getMyStyleHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRecordService.getMyStyleHistory(userDetails.getUser()));
    }
}