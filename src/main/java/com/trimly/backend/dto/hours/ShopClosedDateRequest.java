package com.trimly.backend.dto.hours;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ShopClosedDateRequest(

        @NotNull
        LocalDate closedDate,

        String reason
) {
}