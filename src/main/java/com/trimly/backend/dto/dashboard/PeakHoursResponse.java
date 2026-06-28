package com.trimly.backend.dto.dashboard;

import java.util.List;

public record PeakHoursResponse(
        List<HourSlot> slots
) {
    public record HourSlot(
            int hour,           // 0–23
            String label,       // "10:00 AM"
            long bookingCount
    ) {}
}