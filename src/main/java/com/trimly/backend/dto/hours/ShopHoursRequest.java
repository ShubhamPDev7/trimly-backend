package com.trimly.backend.dto.hours;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ShopHoursRequest(

        @NotNull
        Integer dayOfWeek,

        boolean closed,

        LocalTime openTime,

        LocalTime closeTime
) {
}