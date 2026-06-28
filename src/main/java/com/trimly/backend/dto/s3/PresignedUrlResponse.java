package com.trimly.backend.dto.s3;

public record PresignedUrlResponse(
        String uploadUrl,
        String publicUrl,
        String key
) {}