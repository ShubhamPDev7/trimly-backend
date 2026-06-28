package com.trimly.backend.dto.razorpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayWebhookPayload(
        String event,
        Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            Payment payment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
            Entity entity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entity(
            String id,
            @JsonProperty("order_id") String orderId,
            String status
    ) {}
}