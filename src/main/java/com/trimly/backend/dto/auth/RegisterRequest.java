package com.trimly.backend.dto.auth;

import com.trimly.backend.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        String name,

        @NotBlank
        @Email
        String email,

        String phone,

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull
        Role role

) {
}
