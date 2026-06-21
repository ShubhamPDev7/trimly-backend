package com.trimly.backend.dto.walkin;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WalkInStartRequest(
        @NotNull
        UUID staffId
) {
}
