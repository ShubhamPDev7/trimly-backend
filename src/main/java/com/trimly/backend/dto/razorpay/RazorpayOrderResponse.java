package com.trimly.backend.dto.razorpay;

import java.math.BigDecimal;
import java.util.UUID;


public record RazorpayOrderResponse(
        UUID billId,
        String razorpayOrderId,
        BigDecimal amount,       // in INR (not paise)
        String currency,
        String keyId             // public key — safe to expose
) {
}