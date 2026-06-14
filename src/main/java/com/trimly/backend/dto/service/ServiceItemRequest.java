package com.trimly.backend.dto.service;

import com.trimly.backend.enums.ServiceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ServiceItemRequest(
        @NotNull
        ServiceCategory category,

        @NotBlank
        String name,

        @NotNull
        @Positive
        BigDecimal price,

        @PositiveOrZero
        Integer estTimeMinutes,

        String imageUrl
) {
}
