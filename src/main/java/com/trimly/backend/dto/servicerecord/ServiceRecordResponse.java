package com.trimly.backend.dto.servicerecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceRecordResponse(
        UUID id,
        UUID shopId,
        UUID staffId,
        UUID customerId,
        UUID bookingId,
        UUID walkInQueueEntryId,
        String notes,
        List<String> productsUsed,
        List<String> photoUrls,
        Instant createdAt
) {}