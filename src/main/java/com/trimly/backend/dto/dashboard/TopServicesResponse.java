package com.trimly.backend.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TopServicesResponse(
        List<ServiceStat> services
) {
    public record ServiceStat(
            UUID serviceId,
            String serviceName,
            long bookingCount,
            BigDecimal totalRevenue
    ) {}
}