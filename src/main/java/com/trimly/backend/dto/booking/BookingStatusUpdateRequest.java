package com.trimly.backend.dto.booking;

import com.trimly.backend.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusUpdateRequest(
        @NotNull
        BookingStatus status
) {
}
