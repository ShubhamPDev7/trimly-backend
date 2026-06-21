package com.trimly.backend.dto.walkin;

import com.trimly.backend.enums.WalkInStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WalkInQueueEntryResponse(
        UUID id,
        UUID shopId,
        UUID customerId,
        String guestName,
        String guestPhone,
        UUID preferredStaffId,
        UUID assignedStaffId,
        WalkInStatus status,
        List<WalkInQueueServiceResponse> services,
        Instant joinedAt,
        Instant startedAt,
        Instant completedAt,
        Long estimatedWaitMinutes,
        Instant estimatedStartAt,
        UUID likelyStaffId,
        Integer queuePosition
) {
}
