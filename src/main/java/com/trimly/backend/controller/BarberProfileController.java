package com.trimly.backend.controller;

import com.trimly.backend.dto.barber.BarberProfileRequest;
import com.trimly.backend.dto.barber.BarberProfileResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.BarberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/staff/{staffUserId}/profile")
@RequiredArgsConstructor
public class BarberProfileController {

    private final BarberProfileService barberProfileService;

    @PutMapping
    public ResponseEntity<BarberProfileResponse> upsertProfile(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @RequestBody BarberProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                barberProfileService.upsertProfile(shopId, staffUserId, request, userDetails.getUser().getId()));
    }

    @GetMapping
    public ResponseEntity<BarberProfileResponse> getProfile(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId
    ) {
        return ResponseEntity.ok(barberProfileService.getProfile(shopId, staffUserId));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        barberProfileService.deleteProfile(shopId, staffUserId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}