package com.trimly.backend.dto.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffPerformanceResponse(
        UUID staffId,
        String staffName,
        long bookingsCompleted,
        BigDecimal totalRevenue
) {
}
