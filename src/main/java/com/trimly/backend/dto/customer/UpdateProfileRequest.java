package com.trimly.backend.dto.customer;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank
        String name,

        String phone
) {
}
