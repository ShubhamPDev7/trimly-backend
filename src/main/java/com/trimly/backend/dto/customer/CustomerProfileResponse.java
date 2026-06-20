package com.trimly.backend.dto.customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Instant createdAt
) {
}
