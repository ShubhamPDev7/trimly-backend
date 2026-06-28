package com.trimly.backend.dto.shift;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record StaffShiftRequest(
        @NotNull
        @Min(1) @Max(7)
        Integer dayOfWeek,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        boolean isOff
) {}