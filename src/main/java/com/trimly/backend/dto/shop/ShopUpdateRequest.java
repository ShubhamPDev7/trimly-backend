package com.trimly.backend.dto.shop;

import jakarta.validation.constraints.NotBlank;

public record ShopUpdateRequest(

        @NotBlank
        String name,

        @NotBlank
        String address,

        @NotBlank
        String locality,

        @NotBlank
        String timezone
) {
}