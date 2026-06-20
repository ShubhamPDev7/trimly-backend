package com.trimly.backend.dto.shop;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddStaffRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String roleInShop
) {
}
