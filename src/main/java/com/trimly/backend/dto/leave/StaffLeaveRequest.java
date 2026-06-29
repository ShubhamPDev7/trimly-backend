package com.trimly.backend.dto.leave;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StaffLeaveRequest(
        @NotNull LocalDate leaveDate,
        String reason
) {}