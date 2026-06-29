package com.trimly.backend.dto.policy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancellationPolicyRequest(
        @NotNull @Min(1) Integer minHoursBeforeCancel
) {}