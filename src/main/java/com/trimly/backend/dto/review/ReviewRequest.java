package com.trimly.backend.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReviewRequest(

        @NotNull
        @Min(1) @Max(5)
        Integer rating,

        String comment,

        UUID bookingId,

        UUID walkInQueueEntryId
) {
}