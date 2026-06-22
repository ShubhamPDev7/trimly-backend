package com.trimly.backend.dto.review;

import jakarta.validation.constraints.NotBlank;

public record OwnerReplyRequest(

        @NotBlank
        String reply
) {
}