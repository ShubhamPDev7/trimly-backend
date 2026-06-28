package com.trimly.backend.controller;

import com.trimly.backend.dto.barber.BarberProfileResponse;
import com.trimly.backend.service.BarberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/profiles")
@RequiredArgsConstructor
public class BarberProfileListController {

    private final BarberProfileService barberProfileService;

    @GetMapping
    public ResponseEntity<List<BarberProfileResponse>> getShopProfiles(
            @PathVariable UUID shopId
    ) {
        return ResponseEntity.ok(barberProfileService.getShopProfiles(shopId));
    }
}