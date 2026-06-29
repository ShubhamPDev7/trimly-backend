package com.trimly.backend.dto.subscription;

public record SubscriptionOrderResponse(
        String razorpayOrderId,
        long amountInPaise,
        String currency,
        String plan,
        String shopId
) {}