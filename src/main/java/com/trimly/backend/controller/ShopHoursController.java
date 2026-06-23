package com.trimly.backend.controller;

import com.trimly.backend.dto.hours.ShopClosedDateRequest;
import com.trimly.backend.dto.hours.ShopClosedDateResponse;
import com.trimly.backend.dto.hours.ShopHoursRequest;
import com.trimly.backend.dto.hours.ShopHoursResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ShopHoursService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/hours")
@RequiredArgsConstructor
public class ShopHoursController {

    private final ShopHoursService shopHoursService;

    @PutMapping
    public ResponseEntity<ShopHoursResponse> setHours(
            @PathVariable UUID shopId,
            @Valid @RequestBody ShopHoursRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopHoursResponse response = shopHoursService.setHours(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ShopHoursResponse>> getHours(
            @PathVariable UUID shopId
    ) {
        List<ShopHoursResponse> response = shopHoursService.getHours(shopId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/closed-dates")
    public ResponseEntity<ShopClosedDateResponse> addClosedDate(
            @PathVariable UUID shopId,
            @Valid @RequestBody ShopClosedDateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopClosedDateResponse response = shopHoursService.addClosedDate(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/closed-dates/{closedDateId}")
    public ResponseEntity<Void> removeClosedDate(
            @PathVariable UUID shopId,
            @PathVariable UUID closedDateId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopHoursService.removeClosedDate(shopId, closedDateId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/closed-dates")
    public ResponseEntity<List<ShopClosedDateResponse>> getClosedDates(
            @PathVariable UUID shopId
    ) {
        List<ShopClosedDateResponse> response = shopHoursService.getClosedDates(shopId);
        return ResponseEntity.ok(response);
    }

}