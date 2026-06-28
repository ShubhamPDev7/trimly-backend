package com.trimly.backend.controller;

import com.trimly.backend.dto.referral.MyReferralCodeResponse;
import com.trimly.backend.dto.referral.ReferralResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/my-code")
    public ResponseEntity<MyReferralCodeResponse> getMyCode(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                referralService.getMyReferralCode(shopId, userDetails.getUser()));
    }

    @PostMapping("/apply")
    public ResponseEntity<Void> applyCode(
            @PathVariable UUID shopId,
            @RequestParam String code,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        referralService.applyReferralCode(shopId, code, userDetails.getUser());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ReferralResponse>> getMyReferrals(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                referralService.getMyReferrals(shopId, userDetails.getUser().getId()));
    }
}