package com.trimly.backend.enums;

import java.util.EnumSet;

public enum BookingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(BookingStatus newStatus) {
        return switch (this) {
            case PENDING -> EnumSet.of(ACCEPTED, REJECTED, CANCELLED).contains(newStatus);
            case ACCEPTED -> EnumSet.of(COMPLETED, CANCELLED).contains(newStatus);
            case REJECTED, COMPLETED, CANCELLED -> false;
        };
    }
}
