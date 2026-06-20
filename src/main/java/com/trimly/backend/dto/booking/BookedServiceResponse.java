package com.trimly.backend.dto.booking;

import java.math.BigDecimal;
import java.util.UUID;

public record BookedServiceResponse(
        UUID serviceId,
        String serviceName,
        BigDecimal priceAtBooking
) {
}
