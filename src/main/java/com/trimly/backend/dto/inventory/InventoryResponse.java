package com.trimly.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        UUID shopId,
        String name,
        String description,
        String unit,
        BigDecimal quantityInStock,
        BigDecimal lowStockThreshold,
        BigDecimal costPerUnit,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {}