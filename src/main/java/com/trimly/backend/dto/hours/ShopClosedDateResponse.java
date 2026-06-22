package com.trimly.backend.dto.hours;

import java.time.LocalDate;
import java.util.UUID;

public record ShopClosedDateResponse(
        UUID id,
        UUID shopId,
        LocalDate closedDate,
        String reason
) {
}