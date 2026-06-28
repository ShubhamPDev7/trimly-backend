package com.trimly.backend.dto.barber;

import java.time.Instant;
import java.util.UUID;

public record BarberProfileResponse(
        UUID id,
        UUID shopId,
        UUID userId,
        String staffName,
        String bio,
        String specialties,
        Integer experienceYears,
        String instagramHandle,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt
) {}