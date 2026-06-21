package com.trimly.backend.dto.walkin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record WalkInJoinRequest(

        @NotEmpty
        List<UUID> serviceIds,

        UUID preferredStaffId,

        String guestName,

        String guestPhone

) {
}
