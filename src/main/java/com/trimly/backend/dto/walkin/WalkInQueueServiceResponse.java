package com.trimly.backend.dto.walkin;

import java.math.BigDecimal;
import java.util.UUID;

public record WalkInQueueServiceResponse(
        UUID serviceId,
        String serviceName,
        BigDecimal priceAtJoin
) {
}
