package com.trimly.backend.dto.hours;

import java.time.LocalTime;
import java.util.UUID;

public record ShopHoursResponse(
        UUID id,
        UUID shopId,
        int dayOfWeek,
        boolean closed,
        LocalTime openTime,
        LocalTime closeTime
) {
}