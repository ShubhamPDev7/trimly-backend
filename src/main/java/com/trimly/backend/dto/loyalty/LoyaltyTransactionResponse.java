package com.trimly.backend.dto.loyalty;

import com.trimly.backend.enums.LoyaltyTransactionType;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyTransactionResponse(
        UUID id,
        LoyaltyTransactionType type,
        int points,
        String description,
        Instant createdAt
) {
}