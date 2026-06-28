package com.trimly.backend.controller;

import com.trimly.backend.dto.subscription.SubscriptionResponse;
import com.trimly.backend.enums.SubscriptionPlan;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                subscriptionService.getOrCreateSubscription(shopId, userDetails.getUser().getId()));
    }

    @PatchMapping("/upgrade")
    public ResponseEntity<SubscriptionResponse> upgradePlan(
            @PathVariable UUID shopId,
            @RequestParam SubscriptionPlan plan,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                subscriptionService.upgradePlan(shopId, plan, userDetails.getUser().getId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelSubscription(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        subscriptionService.cancelSubscription(shopId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}