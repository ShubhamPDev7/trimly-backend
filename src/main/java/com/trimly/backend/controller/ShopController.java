package com.trimly.backend.controller;

import com.trimly.backend.dto.shop.*;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @PostMapping
    public ResponseEntity<ShopResponse> createShop(
            @Valid @RequestBody ShopRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResponse response = shopService.createShop(request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shopId}/staff")
    public ResponseEntity<List<ShopStaffResponse>> listStaff(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ShopStaffResponse> response = shopService.listStaff(shopId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shopId}/staff")
    public ResponseEntity<ShopStaffResponse> addStaff(
            @PathVariable UUID shopId,
            @Valid @RequestBody AddStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopStaffResponse response = shopService.addStaff(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{shopId}")
    public ResponseEntity<Void> deleteShop(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopService.deleteShop(shopId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{shopId}/staff/{staffUserId}")
    public ResponseEntity<Void> removeStaff(
            @PathVariable UUID shopId,
            @PathVariable UUID staffUserId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopService.removeStaff(shopId, staffUserId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{shopId}")
    public ResponseEntity<ShopResponse> updateShop(
            @PathVariable UUID shopId,
            @Valid @RequestBody ShopUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResponse response = shopService.updateShop(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

}