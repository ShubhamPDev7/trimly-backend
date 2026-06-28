package com.trimly.backend.dto.subscription;

import com.trimly.backend.enums.SubscriptionPlan;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID shopId,
        SubscriptionPlan plan,
        String status,
        boolean active,
        int maxStaff,
        int maxBookingsPerMonth,
        boolean dashboardEnabled,
        boolean analyticsEnabled,
        boolean multibranchEnabled,
        Instant startedAt,
        Instant expiresAt
) {}