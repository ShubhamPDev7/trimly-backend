package com.trimly.backend.dto.booking;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleRequest(
        @NotNull @FutureOrPresent LocalDate newDate,
        @NotNull LocalTime newTimeSlot
) {}