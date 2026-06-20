package com.trimly.backend.dto.bill;

import com.trimly.backend.enums.PaymentMode;
import com.trimly.backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BillResponse(
        UUID id,
        UUID shopId,
        UUID bookingId,
        BigDecimal totalAmount,
        PaymentMode paymentMode,
        PaymentStatus paymentStatus,
        Instant createdAt
) {
}
