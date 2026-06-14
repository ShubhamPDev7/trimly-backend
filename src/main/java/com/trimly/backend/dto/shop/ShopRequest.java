package com.trimly.backend.dto.shop;

import jakarta.validation.constraints.NotBlank;

public record ShopRequest(
        @NotBlank
        String name,

        String address,

        String locality
) {
}
