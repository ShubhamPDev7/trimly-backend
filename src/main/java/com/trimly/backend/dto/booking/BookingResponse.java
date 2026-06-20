package com.trimly.backend.dto.booking;

import com.trimly.backend.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID shopId,
        UUID customerId,
        UUID staffId,
        String guestName,
        String guestPhone,
        LocalDate bookingDate,
        LocalTime timeSlot,
        BookingStatus status,
        List<BookedServiceResponse> services,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
