package com.trimly.backend.dto.shop;

import java.util.UUID;

public record BarberPublicProfile(
        UUID userId,
        String name,
        String bio,
        String specialties,
        Integer experienceYears,
        String instagramHandle,
        String photoUrl
) {}