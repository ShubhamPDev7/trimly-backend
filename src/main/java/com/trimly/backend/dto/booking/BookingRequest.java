package com.trimly.backend.dto.booking;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingRequest (

    @NotNull
    UUID staffId,

    @NotNull
    @FutureOrPresent
    LocalDate bookingDate,

    @NotNull
    LocalTime timeSlot,

    @NotEmpty
    List<UUID> serviceIds,

    String guestName,

    String guestPhone

    ) {

}




