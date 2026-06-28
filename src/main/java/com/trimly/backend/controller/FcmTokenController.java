package com.trimly.backend.controller;

import com.trimly.backend.dto.fcm.FcmTokenRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.FcmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmService fcmService;

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        fcmService.registerToken(userDetails.getUser().getId(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(
            @RequestParam String token,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        fcmService.removeToken(userDetails.getUser().getId(), token);
        return ResponseEntity.noContent().build();
    }
}