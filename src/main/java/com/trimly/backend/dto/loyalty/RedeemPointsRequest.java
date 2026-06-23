package com.trimly.backend.dto.loyalty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RedeemPointsRequest(

        @NotNull
        UUID billId,

        @NotNull
        @Min(100)
        Integer pointsToRedeem
) {
}