package com.trimly.backend.dto.shop;

import java.time.Instant;
import java.util.UUID;

public record ShopResponse(
        UUID id,
        String name,
        String address,
        String locality,
        UUID ownerId,
        Instant createdAt,
        String token
) {
}
