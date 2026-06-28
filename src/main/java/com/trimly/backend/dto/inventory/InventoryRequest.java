package com.trimly.backend.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryRequest(
        @NotBlank String name,
        String description,
        String unit,
        @NotNull BigDecimal quantityInStock,
        BigDecimal lowStockThreshold,
        BigDecimal costPerUnit
) {}