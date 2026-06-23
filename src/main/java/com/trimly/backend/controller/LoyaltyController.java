package com.trimly.backend.controller;

import com.trimly.backend.dto.loyalty.LoyaltyAccountResponse;
import com.trimly.backend.dto.loyalty.LoyaltyTransactionResponse;
import com.trimly.backend.dto.loyalty.RedeemPointsRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.LoyaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping
    public ResponseEntity<LoyaltyAccountResponse> getAccount(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        LoyaltyAccountResponse response = loyaltyService.getAccount(shopId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<LoyaltyTransactionResponse>> getTransactions(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<LoyaltyTransactionResponse> response = loyaltyService.getTransactions(shopId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/redeem")
    public ResponseEntity<LoyaltyAccountResponse> redeemPoints(
            @PathVariable UUID shopId,
            @Valid @RequestBody RedeemPointsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        LoyaltyAccountResponse response = loyaltyService.redeemPoints(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

}