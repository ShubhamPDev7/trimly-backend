package com.trimly.backend.dto.shift;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record StaffShiftResponse(
        UUID id,
        UUID shopId,
        UUID staffUserId,
        String staffName,
        Integer dayOfWeek,
        String dayName,
        LocalTime startTime,
        LocalTime endTime,
        boolean isOff,
        Instant createdAt,
        Instant updatedAt
) {}