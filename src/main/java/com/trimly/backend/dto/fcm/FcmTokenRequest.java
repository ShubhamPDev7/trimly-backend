package com.trimly.backend.dto.fcm;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(
        @NotBlank String token,
        String deviceType
) {}