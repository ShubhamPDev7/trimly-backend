package com.trimly.backend.dto.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID shopId,
        UUID reviewerId,
        UUID bookingId,
        UUID walkInQueueEntryId,
        int rating,
        String comment,
        String ownerReply,
        Instant ownerRepliedAt,
        Instant createdAt
) {
}