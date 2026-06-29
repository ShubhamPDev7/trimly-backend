package com.trimly.backend.controller;

import com.trimly.backend.dto.policy.CancellationPolicyRequest;
import com.trimly.backend.dto.policy.CancellationPolicyResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.CancellationPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CancellationPolicyController {

    private final CancellationPolicyService cancellationPolicyService;

    @PutMapping("/api/v1/shops/{shopId}/cancellation-policy")
    public ResponseEntity<CancellationPolicyResponse> upsertPolicy(
            @PathVariable UUID shopId,
            @Valid @RequestBody CancellationPolicyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                cancellationPolicyService.upsertPolicy(shopId, request, userDetails.getUser().getId()));
    }

    @GetMapping("/api/v1/shops/{shopId}/cancellation-policy")
    public ResponseEntity<CancellationPolicyResponse> getPolicy(@PathVariable UUID shopId) {
        return cancellationPolicyService.getPolicy(shopId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/api/v1/shops/{shopId}/cancellation-policy")
    public ResponseEntity<Void> deletePolicy(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        cancellationPolicyService.deletePolicy(shopId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}