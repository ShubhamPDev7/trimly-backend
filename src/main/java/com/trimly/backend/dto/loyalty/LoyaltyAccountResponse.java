package com.trimly.backend.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyAccountResponse(
        UUID id,
        UUID shopId,
        UUID customerId,
        int balance,
        int balanceInRupees,
        Instant updatedAt
) {
}