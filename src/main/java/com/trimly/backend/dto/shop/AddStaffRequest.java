package com.trimly.backend.dto.shop;

import com.trimly.backend.enums.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddStaffRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        StaffRole roleInShop
) {
}