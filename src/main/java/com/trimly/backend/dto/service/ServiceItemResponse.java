package com.trimly.backend.dto.service;

import com.trimly.backend.enums.ServiceCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceItemResponse(
        UUID id,
        UUID shopId,
        ServiceCategory category,
        String name,
        BigDecimal price,
        Integer estTimeMinutes,
        String imageUrl
) {
}
