package com.trimly.backend.dto.auth;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String name,
        String email,
        String role
) {
}
