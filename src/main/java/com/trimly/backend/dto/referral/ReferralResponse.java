package com.trimly.backend.dto.referral;

import java.time.Instant;
import java.util.UUID;

public record ReferralResponse(
        UUID id,
        UUID shopId,
        UUID referrerId,
        UUID referredId,
        String referralCode,
        String status,
        Integer pointsAwarded,
        Instant createdAt,
        Instant completedAt
) {}