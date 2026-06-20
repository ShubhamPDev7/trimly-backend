package com.trimly.backend.dto.bill;

import com.trimly.backend.enums.PaymentMode;
import jakarta.validation.constraints.NotNull;

public record BillRequest(
        @NotNull
        PaymentMode paymentMode
) {
}
