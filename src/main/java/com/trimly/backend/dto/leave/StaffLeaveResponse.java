package com.trimly.backend.dto.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffLeaveResponse(
        UUID id,
        UUID shopId,
        UUID staffUserId,
        String staffName,
        LocalDate leaveDate,
        String reason,
        Instant createdAt
) {}