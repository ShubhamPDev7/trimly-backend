package com.trimly.backend.dto.review;

public record ShopRatingSummaryResponse(
        double averageRating,
        long totalReviews
) {
}