package com.trimly.backend.dto.barber;

public record BarberProfileRequest(
        String bio,
        String specialties,
        Integer experienceYears,
        String instagramHandle,
        String photoUrl
) {}