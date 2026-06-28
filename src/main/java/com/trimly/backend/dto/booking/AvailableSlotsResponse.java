package com.trimly.backend.dto.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AvailableSlotsResponse(
        UUID shopId,
        UUID staffId,
        LocalDate date,
        int slotIntervalMinutes,
        List<LocalTime> availableSlots
) {}