package com.trimly.backend.dto.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryUsageRequest(
        @NotNull UUID inventoryItemId,
        @NotNull BigDecimal quantityUsed
) {}