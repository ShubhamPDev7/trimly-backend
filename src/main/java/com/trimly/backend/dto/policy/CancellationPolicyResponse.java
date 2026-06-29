package com.trimly.backend.dto.policy;

import java.time.Instant;
import java.util.UUID;

public record CancellationPolicyResponse(
        UUID id,
        UUID shopId,
        int minHoursBeforeCancel,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}